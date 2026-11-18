# QuizForge — AI Quiz & Assessment Platform

[![CI](https://github.com/HarshalRaneHR/quizforge/actions/workflows/ci.yml/badge.svg)](https://github.com/HarshalRaneHR/quizforge/actions/workflows/ci.yml)

Instructors build quizzes with AI, publish them and assign them to classrooms; learners explore, take them and track their progress. Learners can also turn their own notes into practice quizzes. A built-in library of 100 questions (Java, Python, LLMs, El Niño, SQL) is available from the first start.

**Stack:** React 19 + TypeScript (Vite, Tailwind CSS, shadcn/ui, TanStack Query, React Router, Recharts) · Spring Boot 4 · Java 25 · Spring Security (JWT + refresh tokens) · Spring Data JPA / Hibernate · PostgreSQL 16 + Flyway · Spring AI (Ollama) · springdoc OpenAPI · JUnit 5, Mockito, Testcontainers · Docker Compose · GitHub Actions

## Features

- **7 question types** — multiple choice, multi-select, true/false, fill in the blank, numeric, ordering, matching — each with its own validation and grading, including **partial credit**
- **AI generation from a blueprint** — choose the number of questions, split them across types (by hand, *Equal*, *Random* or presets) and pick a difficulty; runs as a **background job** with live progress, rate-limited per user
- **Studio for instructors** — create drafts, edit every question in a per-type editor, reorder, generate more with AI, set quiz rules, then **publish → archive / restore / duplicate**
- **Built-in library** — five 20-question quizzes that are always available; read-only and **undeletable** (enforced by the API and a database trigger); instructors can duplicate or assign them
- **Explore** — learners find published quizzes and take them; answer keys never reach the browser before grading
- **Attempt engine** — start/resume attempts, answers **autosave** (close the tab, come back later), flag questions for review, question navigator; per-quiz **max attempts, pass mark, shuffled questions/options** and **score-only** results; review past attempts
- **Classrooms** — join with a code, assignments with due dates, learner progress, gradebook with CSV export
- **Analytics** — learner score trend, streak and accuracy per question type; per-question difficulty and most common wrong answers for instructors; platform overview for admins
- **Roles** — Learner, Instructor, Admin, built from fine-grained permissions; admins create users and change roles

## Architecture

```
┌──────────────────────────┐       /api/*        ┌─────────────────────────────┐
│ React + TS (Vite)        │ ──────────────────▶ │ Spring Boot (port 8080)     │
│ shadcn/ui, TanStack Query│   Bearer <JWT>      │ Security (JWT filter, RBAC) │
│ nginx in Docker (:3000)  │ ◀────────────────── │ Controllers → Services      │
└──────────────────────────┘  JSON + HttpOnly    │ JPA + Flyway ──┐            │
                               refresh cookie    │ Spring AI ─────┼──▶ Ollama  │
                                                 └────────────────┼────────────┘
                                                                  ▼
                                                         PostgreSQL 16 (JSONB)
```

## Run locally

Prerequisites: Docker Desktop, [Ollama](https://ollama.com) with `ollama pull llama3.1` (only needed for AI generation).

```bash
cp .env.example .env      # fill in JWT_SECRET (Base64, 32+ bytes) and admin credentials
docker compose up --build
```

Open http://localhost:3000 and log in with the admin account from `.env`. The quiz library is ready in **Explore**.

**Try it with demo data:** set `DEMO_DATA=true` in `.env` before starting. It creates `demo-instructor@quizforge.dev` and `demo-learner@quizforge.dev` (password from `DEMO_PASSWORD`, default `demo-password-123`), a demo quiz and a classroom with join code `QFDEMO26`. Never enable it on a real deployment.

### Development mode

```bash
docker compose up -d db                       # or use a local PostgreSQL (see .env)
cd backend && ./mvnw spring-boot:run          # reads ../.env automatically
cd frontend && npm install && npm run dev     # http://localhost:5173, proxies /api to :8080
```

- Backend tests: `cd backend && ./mvnw test` (integration tests start PostgreSQL with Testcontainers, so Docker must be running)
- Regenerate frontend API types after backend changes (backend running): `cd frontend && npm run gen:api`
- Health: `GET /actuator/health` (also `/liveness` and `/readiness`)

### Configuration

| Variable | Default | Purpose |
|---|---|---|
| `JWT_SECRET` | — | Base64 key (32+ bytes) for signing access tokens |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | — | Admin account created on first start |
| `OLLAMA_BASE_URL`, `OLLAMA_MODEL` | — / `llama3.1` | Model server for AI generation |
| `AI_GENERATIONS_PER_HOUR` | `10` | Per-user AI generation limit (429 + `Retry-After` when exceeded) |
| `DEMO_DATA`, `DEMO_PASSWORD` | `false` | Seed demo accounts, quiz and classroom |
| `API_DOCS_ENABLED` | `true` | Swagger UI and OpenAPI spec |
| `COOKIE_SECURE` | `true` | HTTPS-only refresh cookie (browsers accept it on localhost) |

## API

Interactive docs (Swagger UI): **http://localhost:8080/swagger-ui.html** · OpenAPI spec: `/v3/api-docs`.
To try protected endpoints: call `POST /api/auth/login`, copy `token`, click **Authorize** and paste it.

| Area | Endpoints |
|---|---|
| Auth | `POST /api/auth/register · login · refresh · logout`, `GET /api/me` |
| Practice & catalog | `POST /api/quizzes/generate` (background job), `GET /api/generation-jobs/{id}`, `GET /api/quizzes`, `GET /api/quizzes/explore` |
| Taking quizzes | `GET /api/quizzes/{id}` (landing), `POST /api/quizzes/{id}/attempts` (start/resume), `PUT /api/attempts/{id}/answers/{questionId}` (autosave), `POST /api/attempts/{id}/submit`, `GET /api/attempts/{id}` (review), `GET /api/attempts/me` |
| Studio (instructor) | `/api/studio/quizzes` — create, edit, settings, questions CRUD + reorder, AI generate, publish / unpublish / archive / restore / duplicate, analytics |
| Classrooms | `/api/classrooms` — create, join by code, members, assignments, gradebook (+ CSV); `GET /api/assignments/me` |
| Analytics | `GET /api/analytics/me`, `GET /api/studio/quizzes/{id}/analytics`, `GET /api/admin/stats` |
| Admin | `GET/POST /api/admin/users`, `PATCH /api/admin/users/{id}/role` |

## Design decisions

- **Strategy pattern for question types:** one `QuestionTypeHandler` per type (validation + grading) behind a registry; adding a type touches no `switch` elsewhere.
- **JSONB for type-specific data:** one `question` table with `content` (shown to learners) and `answer_key` (never sent before grading), mapped to sealed interfaces + records.
- **Server-side grading and shuffling:** answers are graded on the server; ordering/matching items are stored shuffled so the display order never leaks the answer.
- **LLM output is never trusted:** every generated question is validated by its type handler; invalid ones are dropped.
- **Background generation jobs** on virtual threads with progress polling, one job per user, a Bucket4j token-bucket limit, and fail-fast when the model is unreachable — no HTTP request held open for minutes.
- **Attempts are server-side state:** one open attempt per learner and quiz is enforced by a partial unique index; answers autosave per question; grading uses only what's saved; shuffling is derived from a per-attempt seed so a resumed attempt looks identical; questions are only sent after "Start".
- **Quiz lifecycle protects results:** questions are editable only in DRAFT; quizzes learners have taken are archived or duplicated, never edited or deleted.
- **Defence in depth for the library:** built-in quizzes are blocked from editing/deleting in the service layer *and* by a PostgreSQL trigger; their content lives in versioned JSON and is validated by a unit test.
- **Aggregates in the database:** the gradebook and analytics use grouped JPQL with interface projections — one query instead of one per cell.
- **Security:** 15-minute access tokens in memory, rotating refresh tokens in an HttpOnly SameSite=Strict cookie (stored hashed, reuse detection), permission-based method security, 404 instead of 403 for resources you can't see, CSV export protected against formula injection, non-root container.
- **Schema managed by Flyway** (`ddl-auto: validate`), N+1 avoided with entity graphs, batch fetching and denormalised counts.
- **Tested against the real thing:** unit tests for the domain rules plus Testcontainers integration tests that boot the app on PostgreSQL; CI runs both, the frontend lint/build and the Docker image builds on every push.

> **Note for Windows contributors:** `@emnapi/*` are pinned as dev dependencies on purpose. Without them, `npm install` on Windows drops these Linux-only optional packages from `package-lock.json` and `npm ci` then fails in Docker and CI.

## License

MIT
