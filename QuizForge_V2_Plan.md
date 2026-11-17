# QuizForge v2 — Plan

**Goal:** turn QuizForge from a single-user MCQ demo into a role-based assessment platform that reads like real product work on a **React + Spring Boot developer (3+ years)** resume.

**Pitch after v2:** *An AI-powered assessment platform where instructors generate, review and publish quizzes with 7 question types, assign them to classrooms with deadlines and time limits, and track learner performance through analytics dashboards.*

---

## 1. What changes from v1

| Area | v1 (today) | v2 |
|---|---|---|
| Question types | MCQ only | 7 types (see §2) |
| Who creates quizzes | Everyone, private only | Instructors/Admins create; learners take published/assigned quizzes |
| AI generation | "N MCQs" | **Blueprint**: how many of each type, difficulty mix, topics |
| Editing | None — AI output saved as-is | Draft → review/edit every question → publish |
| Taking a quiz | One page, no rules | Timer, autosave, resume, shuffle, attempt limits, negative marking |
| Grading | Exact match | Per-type strategies, partial credit (AI grading for written answers later) |
| Roles | USER / ADMIN | ADMIN / INSTRUCTOR / LEARNER, permission-based |
| Insights | Score history | Analytics dashboards, per-question stats, leaderboards, weak topics |
| Engineering | Basics | Flyway, refresh tokens, OpenAPI, Testcontainers, CI, observability, rate limiting |

---

## 2. Question types

| Type | What the learner does | Grading |
|---|---|---|
| **SINGLE_CHOICE** (MCQ) | Pick 1 of N | Exact |
| **MULTIPLE_CHOICE** (MCMS) | Pick all that apply | All-or-nothing **or** partial credit (configurable) |
| **TRUE_FALSE** | True / False | Exact |
| **FILL_BLANK** | Type a word/phrase into a blank (can have several blanks) | Normalised match (case, whitespace) against accepted answers |
| **NUMERIC** | Enter a number | Within tolerance (e.g. ±0.5) |
| **ORDERING** | Drag items into the correct sequence | Exact or partial (items in correct position) |
| **MATCHING** | Match left items to right items | Partial credit per correct pair |
| **SHORT_ANSWER** *(deferred — see §9)* | Write 1–3 sentences | **AI-graded** against a rubric/model answer, with score + feedback; instructor can override |

**Per-question settings:** points, difficulty (EASY/MEDIUM/HARD), topic tags, explanation, optional negative marks.

### Scoring design (strong interview topic)
A `QuestionGrader` interface with one implementation per type (**Strategy pattern**), chosen by a registry keyed on `QuestionType`. Adding a new type = one new class, no `switch` statements spread across the code.

```java
public interface QuestionGrader<C extends QuestionContent, R extends LearnerResponse> {
    QuestionType type();
    GradeResult grade(C content, AnswerKey key, R response, ScoringPolicy policy);
}
```

### Storage design (strong interview topic)
One `question` table with a `type` column and two **PostgreSQL JSONB** columns:
- `content` — what the learner sees (prompt, options, blanks, items to order…)
- `answer_key` — never sent to learners

Mapped to Java **sealed interfaces + records** via Jackson polymorphic types and Hibernate's `@JdbcTypeCode(SqlTypes.JSON)`. Trade-off vs table-per-type: flexible schema and simple queries, at the cost of DB-level constraints — validation moves into the domain layer (be ready to discuss this).

---

## 3. Roles & permissions

| Capability | LEARNER | INSTRUCTOR | ADMIN |
|---|:-:|:-:|:-:|
| Take published / assigned quizzes | ✅ | ✅ | ✅ |
| Personal practice quizzes from own notes (AI) | ✅ (limited per day) | ✅ | ✅ |
| Create / edit / publish quizzes, question bank | ❌ | ✅ own | ✅ all |
| Create classrooms, assign quizzes, see learners' results | ❌ | ✅ own classes | ✅ all |
| Override grades *(with AI grading, later)* | ❌ | ✅ own quizzes | ✅ |
| Manage users & roles, platform analytics, audit log | ❌ | ❌ | ✅ |

**Implementation:**
- Roles map to fine-grained **authorities** (`QUIZ_CREATE`, `QUIZ_PUBLISH`, `CLASS_MANAGE`, `USER_MANAGE`…).
- `@PreAuthorize` on service methods, plus **ownership checks** (an instructor can only edit their own quizzes) via a custom `PermissionEvaluator`.
- Frontend: a `<Can permission="QUIZ_CREATE">` component and role-based routes/menus. The backend is still the source of truth.

---

## 4. Features

### 4.1 Quiz builder (Instructor)
- **AI blueprint generation:** choose counts per type (e.g. 5 MCQ, 3 MCMS, 2 True/False, 2 Fill-blank, 1 Ordering, 1 Matching), difficulty mix (e.g. 30/50/20), topics, and language level.
- **Sources:** paste text **or upload PDF/DOCX/TXT** (Apache Tika extracts the text).
- **Async generation:** the request returns a `jobId` immediately; progress streams to the UI over **Server-Sent Events** ("Generated 7/15 questions…"). Runs on **virtual threads** (Java 25).
- **Review screen:** edit any question, regenerate a single question, change its type, reorder questions by drag-and-drop, delete or add questions manually.
- **Lifecycle:** `DRAFT → PUBLISHED → ARCHIVED`. Published quizzes are versioned, so editing never corrupts past attempts.
- **Question bank:** save questions with tags; build quizzes by hand-picking or by **random draw** ("10 random MEDIUM questions tagged `java-oop`").

### 4.2 Quiz settings
Time limit · max attempts · passing score · shuffle questions/options · negative marking · availability window (opens/closes) · **result visibility** (immediately / after the deadline / score only / never) · one-question-per-page or all-at-once.

### 4.3 Attempt engine (Learner)
- **Start attempt:** the server records the start time and deadline; the timer is **server-enforced**, not just shown in the UI.
- **Autosave** every answer (debounced). Closing the tab and returning resumes the attempt.
- **Per-attempt shuffle** with a stored seed, so the review shows the same order the learner saw.
- **Question navigator** (answered / flagged / unanswered), **flag for review**, keyboard shortcuts.
- **Idempotent submit:** double-clicking or retrying never creates two attempts. Expired attempts auto-submit.
- **Results:** per-question breakdown, explanations, topic-wise score.

### 4.4 Classrooms & assignments
- An instructor creates a classroom and learners join with a **join code**.
- Assign a quiz to a classroom with a **due date**; learners see "Assigned to me" with due/overdue badges.
- Instructor gradebook: learners × assignments table, CSV export.

### 4.5 Analytics
- **Learner dashboard:** score trend line, accuracy by topic (radar/bar chart), streak counter, "weak topics".
- **Review mistakes mode:** a practice quiz built from questions you got wrong (simple spaced repetition).
- **Instructor quiz analytics:** score distribution, average time, **per-question difficulty index** (% correct), most common wrong options. Helps find badly written questions.
- **Leaderboard** per quiz/classroom (opt-in).
- **Admin:** platform stats (users, quizzes, attempts per day, AI usage).

### 4.6 Platform
- Notifications (in-app; email optional) for new assignments and grades.
- **Audit log** of admin and instructor actions (who published, who changed a role, who overrode a grade).
- Rate limiting on AI endpoints (Bucket4j) and a per-user daily AI quota.

---

## 5. Architecture & tech additions

### Backend
| Addition | Why it matters on a 3+ yr resume |
|---|---|
| **Flyway** migrations (drop `ddl-auto: update`) | Production schema management |
| **Refresh tokens** (rotation, stored hashed, revocable) + httpOnly cookie | Real-world auth, not toy JWT |
| **Method security** `@PreAuthorize` + `PermissionEvaluator` | Fine-grained RBAC with ownership |
| **springdoc-openapi** (Swagger UI) | Documented API, used to generate frontend types |
| **MapStruct** DTO mapping | Clean layering |
| **JSONB + sealed records** for question content | Modern Java + Postgres skills |
| **Strategy pattern** graders | Design patterns, extensible |
| **Async jobs + SSE** on virtual threads | Concurrency, streaming |
| **Optimistic locking** (`@Version`) on quiz editing | Concurrent-edit safety |
| **JPA Specifications** for search/filter/sort | Dynamic queries |
| **Caffeine cache** for published quizzes | Performance |
| **Bucket4j** rate limiting | Protecting expensive endpoints |
| **Actuator + Micrometer + Prometheus/Grafana** | Observability |
| **Testcontainers** integration tests, `@WebMvcTest` security tests | Real testing culture |
| **Spring AI provider via profiles** (Ollama dev, hosted API prod) | Deployable AI |

Package layout stays **feature-based**: `auth`, `user`, `quiz`, `question`, `attempt`, `grading`, `classroom`, `analytics`, `ai`, `common`.

### Frontend
| Addition | Why |
|---|---|
| **Tailwind CSS + shadcn/ui (Radix)** | Modern, accessible, attractive UI that's common in job posts |
| **React Hook Form + Zod** | Complex forms (quiz builder, blueprint) with validation |
| **openapi-typescript** generated types | Frontend/backend contract kept in sync automatically |
| **dnd-kit** | Ordering/matching questions, drag-reorder in the builder |
| **Recharts** | Analytics charts |
| **TanStack Table** | Admin users, gradebook |
| **Framer Motion** | Polished transitions (question changes, results reveal) |
| **Lazy-loaded routes** per role | Code splitting |
| **Vitest + React Testing Library + MSW** | Component tests with a mocked API |
| **Playwright** E2E | Critical flows tested end-to-end |
| Light/dark **theme toggle**, toasts, skeleton loaders, empty states | Product polish |

### UI sketch
- **Learner:** Home (assigned quizzes, continue attempt, streak, recent scores) → Explore → Take quiz (focus mode, timer, navigator) → Results.
- **Instructor:** Dashboard → Quizzes (table with status filters) → Builder (blueprint → generate → review/edit → settings → publish) → Classrooms → Gradebook → Quiz analytics.
- **Admin:** Users & roles · Platform analytics · Audit log.
- Sidebar layout with role-based menus, a command palette (Ctrl+K), and responsive down to mobile.

### Quality & delivery
- **GitHub Actions:** backend tests (with Testcontainers) + frontend lint/test/build on every PR.
- **Docker Compose** for the full stack; **deploy:** backend on Render/Railway, Postgres on Neon, frontend on Vercel, hosted AI API.
- **Seed data** (demo admin/instructor/learner accounts and sample quizzes) so recruiters can try it immediately.

---

## 6. Data model (v2)

```
users (id, email, password_hash, display_name, role, enabled, created_at)
refresh_tokens (id, user_id, token_hash, expires_at, revoked_at)

quiz (id, owner_id, title, description, status[DRAFT|PUBLISHED|ARCHIVED], version,
      settings JSONB, created_at, updated_at, published_at)
question (id, quiz_id NULLABLE, bank_owner_id, type, difficulty, points,
          content JSONB, answer_key JSONB, explanation, position)
question_tag (question_id, tag)

classroom (id, owner_id, name, join_code)
classroom_member (classroom_id, user_id, joined_at)
assignment (id, classroom_id, quiz_id, opens_at, due_at)

attempt (id, quiz_id, user_id, assignment_id NULL, status[IN_PROGRESS|SUBMITTED|EXPIRED|GRADED],
         started_at, deadline_at, submitted_at, shuffle_seed, score, max_score)
attempt_answer (attempt_id, question_id, response JSONB, points_awarded, feedback,
                graded_by[AUTO|AI|INSTRUCTOR], flagged, updated_at)

generation_job (id, user_id, status, blueprint JSONB, progress, error, quiz_id, created_at)
audit_log (id, actor_id, action, entity_type, entity_id, details JSONB, created_at)
```

---

## 7. Roadmap

Each phase ends with something working and demoable. Build them in order.

| Phase | Scope | Est. |
|---|---|---|
| **A. Foundation** | Flyway; 3 roles + authorities; method security; refresh tokens; OpenAPI; frontend move to Tailwind + shadcn/ui; app shell with sidebar and role-based routes; generated API types | 1 week |
| **B. Question types + grading** | JSONB content model, 7 types, `QuestionGrader` strategies, partial credit, unit tests for every grader | 1 week |
| **C. Quiz builder** | Manual create/edit for all types; AI blueprint generation (async job + SSE); review/edit screen; drag-reorder; publish lifecycle; question bank | 1.5 weeks |
| **D. Attempt engine** | Settings, server timer, autosave/resume, shuffle, navigator, attempt limits, result visibility | 1 week |
| **E. Classrooms** | Classrooms, join codes, assignments with due dates, gradebook, CSV export | 0.5–1 week |
| **F. Analytics** | Learner dashboard, review-mistakes mode, instructor per-question analytics, leaderboard, admin stats | 1 week |
| **G. Production quality** | Testcontainers + MockMvc security tests, Vitest/RTL, Playwright; CI; rate limiting; Actuator/metrics; file upload; deploy + seed data; README with screenshots/GIF | 1 week |

**Total:** about 6–8 weeks part-time. **Phases A–D alone** already make a strong project. E–G are what take it from good to stand-out.

---

## 8. Resume bullets this enables

- Built an AI-powered assessment platform (React 19, TypeScript, Spring Boot, PostgreSQL) supporting **7 question types** with a pluggable **strategy-based grading engine** and partial credit.
- Implemented **role-based access control** (Admin/Instructor/Learner) with Spring Security method-level authorization, ownership checks and **JWT refresh-token rotation**.
- Designed **asynchronous AI quiz generation** with Spring AI, streaming progress to the UI over **Server-Sent Events**; validated LLM output against each question type's rules before saving.
- Built a **server-enforced timed attempt engine** with autosave, resume, per-attempt shuffling and idempotent submission.
- Delivered **analytics dashboards** (per-question difficulty, topic accuracy, score distributions) with Recharts and optimised PostgreSQL queries.
- Ensured quality with **Testcontainers** integration tests, React Testing Library, Playwright E2E and GitHub Actions CI; shipped with Docker, Flyway and Prometheus metrics.

---

## 9. Decisions (agreed)

1. **Roles:** 3 roles — Admin / Instructor / Learner.
2. **UI stack:** Tailwind CSS + shadcn/ui.
3. **Scope:** build **Phases A–D first**, test end to end, then continue with E–G.
4. **AI grading:** **deferred.** v2 ships with **7 question types** (SHORT_ANSWER is left out). AI rubric grading and SHORT_ANSWER are added later, when switching to a hosted AI API. The grader registry makes this a drop-in addition.
