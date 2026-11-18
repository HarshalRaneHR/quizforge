# QuizForge

[![CI](https://github.com/HarshalRaneHR/quizforge/actions/workflows/ci.yml/badge.svg)](https://github.com/HarshalRaneHR/quizforge/actions/workflows/ci.yml)
![Java 25](https://img.shields.io/badge/Java-25-orange)
![Spring Boot 4](https://img.shields.io/badge/Spring%20Boot-4-6DB33F)
![React 19](https://img.shields.io/badge/React-19-61DAFB)
![License: MIT](https://img.shields.io/badge/License-MIT-blue)

**A self-hosted, invite-only quiz and learning platform for a school, coaching institute or company.**

One administrator decides who gets an account and what each person may do. Instructors build quizzes by hand or with AI, publish them and assign them to classrooms. Learners take them, get graded instantly and track their progress. AI runs on your own machine through Ollama, so notes never leave the organization. A library of 100 ready-made questions is available from the first start.

---

## Contents

- [Features](#features)
- [Tech stack](#tech-stack)
- [Quick start](#quick-start)
- [Using QuizForge as a school admin](#using-quizforge-as-a-school-admin)
- [Development setup](#development-setup)
- [Configuration](#configuration)
- [API](#api)
- [Project structure](#project-structure)
- [Design decisions](#design-decisions)
- [Testing](#testing)
- [Known limitations](#known-limitations)
- [License](#license)

---

## Features

### For admins

- **Invite-only accounts.** There is no public sign-up by default. The admin adds each person with a name, email and role.
- **One-time passwords.** QuizForge generates a temporary password such as `k7Qm-3xPa-9vTz`, shows it once and offers a "Copy login details" button. Every new person must choose their own password at first login.
- **Per-person access.** Besides the role, single abilities can be switched on or off per person: AI practice quizzes, the quiz Studio and running classrooms.
- **Account care.** Reset a forgotten password, either with a generated one-time password or one the admin types. Deactivate someone who left: they are signed out at once, and all their results stay. Reactivate at any time.
- **Overview.** Platform statistics, plus a users list with status (Active, Pending, Deactivated), last login, search and filters.
- **Answer keys.** View any quiz with all correct answers and explanations without taking it.

### For instructors

- **Studio.** Create quizzes, edit every question in an editor made for its type, reorder questions, and set the rules: attempts allowed, pass mark, shuffling and whether learners see answers.
- **AI generation from notes.** Paste notes, choose the number of questions and split them across question types by hand, *Equal* or *Random*. Generation runs in the background with live progress.
- **Quiz lifecycle.** Draft → publish → archive / restore / duplicate. Quizzes that learners have taken are protected from edits.
- **Classrooms.** Create a class, share its join code, assign quizzes with optional due dates, and follow the gradebook with CSV export.
- **Quiz analytics.** Per-question difficulty and the most common wrong answers.

### For learners

- **Explore and take quizzes** with autosave and resume, a question navigator and flag-for-review. There are no time limits.
- **Classes.** Join with a code and see assigned quizzes sorted by due date.
- **Results.** Instant server-side grading with partial credit and explanations, and reviewable past attempts.
- **Progress.** Score trend, streak and accuracy per question type.
- **AI practice quizzes** generated from their own notes, if the admin allows it.

### Platform

- **7 question types:** multiple choice, multi-select, true/false, fill in the blank, numeric, ordering and matching.
- **Built-in library:** five 20-question quizzes on Java, Python, LLMs, El Niño and SQL. They are read-only and can't be deleted, but can be copied into the Studio.
- **Light and dark theme**, a responsive layout, and accessible components.

---

## Tech stack

| Layer | Technologies |
|---|---|
| Frontend | React 19, TypeScript, Vite, Tailwind CSS 4, shadcn/ui (Radix), TanStack Query, React Router 7, Recharts, sonner |
| Backend | Java 25, Spring Boot 4, Spring Security (JWT + rotating refresh tokens), Spring Data JPA / Hibernate, Bean Validation, springdoc OpenAPI, Actuator, Bucket4j |
| AI | Spring AI with Ollama, structured output, any local model such as `llama3.1` |
| Database | PostgreSQL 16 with JSONB, schema managed by Flyway |
| Testing | JUnit 5, Mockito, MockMvc, Testcontainers |
| DevOps | Docker, Docker Compose, nginx, GitHub Actions |

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

---

## Quick start

**You need:** Docker Desktop. For AI generation, also install [Ollama](https://ollama.com) and run `ollama pull llama3.1`. Everything else works without it, including the quiz library.

1. Clone the repository and create your settings file:

   ```bash
   git clone https://github.com/HarshalRaneHR/quizforge.git
   cd quizforge
   cp .env.example .env
   ```

2. Edit `.env`:
   - Set `JWT_SECRET` to a random Base64 value of 32 bytes or more. The commands to generate one are in `.env.example`.
   - Set `ADMIN_EMAIL` and `ADMIN_PASSWORD` for the first admin account.

3. Start everything:

   ```bash
   docker compose up --build
   ```

4. Open **http://localhost:3000** and log in with the admin email and password from `.env`. QuizForge asks you to choose a new password right away. The `.env` password only works for that first login.

---

## Using QuizForge as a school admin

1. **Add your teachers and students.** Go to **Users → Add user**. Enter the name, email and role (Instructor for teachers, Learner for students), then adjust the ticked abilities if needed. Click **Create user**, then **Copy login details** and send them to the person.
2. **They log in for the first time** with their email and the temporary password, and choose their own password.
3. **Create a classroom.** Go to **Classrooms → New classroom**. Admins and instructors can do this.
4. **Prepare a quiz.** Build one in **Studio**, or use a library quiz: open it in **Explore**, copy it to the Studio and publish the copy.
5. **Assign it.** In the classroom, choose **Assign a quiz**, pick the quiz and an optional due date.
6. **Students join** under **My classes** with the classroom's join code. The assigned quiz then appears on their home page.
7. **Follow results** in the classroom's **Gradebook**, and export them as CSV.

**When someone forgets their password:** open the row menu on the Users page and choose **Reset password**.
**When someone leaves:** choose **Deactivate**. Their results stay in every gradebook.

---

## Development setup

```bash
docker compose up -d db                       # or use a local PostgreSQL (see .env)
cd backend && ./mvnw spring-boot:run          # API on http://localhost:8080, reads ../.env
cd frontend && npm install && npm run dev     # UI on http://localhost:5173, proxies /api to :8080
```

- **Swagger UI:** http://localhost:8080/swagger-ui.html. Call `POST /api/auth/login`, copy `token`, click **Authorize** and paste it.
- **API types:** after changing the backend, with it running, regenerate the frontend types with `cd frontend && npm run gen:api`.
- **Health:** `GET /actuator/health`, plus `/liveness` and `/readiness`.
- **Restart the backend** after pulling or changing backend code. The frontend reloads by itself; the backend doesn't.

### Demo data

Set `DEMO_DATA=true` in `.env` before starting. It creates `demo-instructor@quizforge.dev` and `demo-learner@quizforge.dev` with the password from `DEMO_PASSWORD` (default `demo-password-123`), a demo quiz and a classroom with join code `QFDEMO26`. Never enable it on a real deployment.

---

## Configuration

All settings come from environment variables or `.env`. The `.env` file is never committed; `application.yaml` contains only placeholders.

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | — | PostgreSQL connection. Set automatically by Docker Compose. |
| `JWT_SECRET` | — | Base64 key of 32+ bytes for signing access tokens |
| `COOKIE_SECURE` | `true` | HTTPS-only refresh cookie. Browsers still accept it on `localhost`. |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | — | First admin account, created once on an empty database |
| `ADMIN_REQUIRE_PASSWORD_CHANGE` | `true` | The first admin must replace the `.env` password at first login |
| `PUBLIC_SIGNUP` | `false` | `true` lets anyone create a Learner account; otherwise only admins add people |
| `OLLAMA_BASE_URL`, `OLLAMA_MODEL` | — / `llama3.1` | Model server and model for AI generation |
| `AI_GENERATIONS_PER_HOUR` | `10` | AI generations per person per hour (then 429 with `Retry-After`) |
| `DEMO_DATA`, `DEMO_PASSWORD` | `false` | Seed demo accounts, a quiz and a classroom |
| `API_DOCS_ENABLED` | `true` | Swagger UI and the OpenAPI spec |

Failed logins are limited to 5 per email within 15 minutes.

---

## API

Full interactive documentation is in Swagger UI. Main endpoints:

| Area | Endpoints |
|---|---|
| Auth | `GET /api/auth/config` · `POST /api/auth/login · refresh · logout · register` (register only when public sign-up is on) |
| Account | `GET /api/me` · `POST /api/account/password` · `PUT /api/account/profile` |
| Practice & catalog | `POST /api/quizzes/generate` (background job) · `GET /api/generation-jobs/{id}` · `GET /api/quizzes` · `GET /api/quizzes/explore` · `GET /api/quizzes/{id}/answer-key` |
| Taking quizzes | `GET /api/quizzes/{id}` · `POST /api/quizzes/{id}/attempts` (start or resume) · `PUT /api/attempts/{id}/answers/{questionId}` (autosave) · `POST /api/attempts/{id}/submit` · `GET /api/attempts/{id}` · `GET /api/attempts/me` |
| Studio | `/api/studio/quizzes`: create, edit, settings, questions CRUD and reorder, AI generate, publish / unpublish / archive / restore / duplicate, analytics |
| Classrooms | `/api/classrooms`: create, join by code, members, assignments, gradebook and CSV · `GET /api/assignments/me` |
| Analytics | `GET /api/analytics/me` · `GET /api/studio/quizzes/{id}/analytics` · `GET /api/admin/stats` |
| Admin | `GET/POST /api/admin/users` · `PATCH …/{id}/role` · `PUT …/{id}/permissions` · `POST …/{id}/reset-password · deactivate · reactivate` · `GET …/access-model` |

`POST /api/admin/users/{id}/reset-password` without a body generates a one-time password. With `{"password": "...", "requireChange": false}` it sets that exact password.

Errors use the standard [RFC 7807](https://www.rfc-editor.org/rfc/rfc7807) Problem Details format.

---

## Project structure

```
quizforge/
├── backend/                         Spring Boot application
│   └── src/main/java/com/harshal/quizforge/
│       ├── auth/                    users, roles, permissions, JWT, refresh tokens, admin user management
│       ├── quiz/                    quizzes, Studio, attempts, AI generation jobs
│       ├── question/                one handler per question type (validation + grading)
│       ├── classroom/               classrooms, assignments, gradebook
│       ├── analytics/               learner, quiz and platform statistics
│       ├── library/                 built-in quiz seeding
│       ├── demo/                    optional demo data
│       └── common/                  errors, paging, configuration
│   └── src/main/resources/
│       ├── db/migration/            Flyway migrations V1 … V10
│       └── seed/quizzes/            library quizzes as JSON
├── frontend/                        React application
│   └── src/
│       ├── pages/                   one folder or file per screen
│       ├── components/              app components and shadcn/ui primitives
│       ├── api/                     HTTP client and generated OpenAPI types
│       └── auth/, hooks/, lib/      session, shared hooks and helpers
├── docker-compose.yml               db + backend + frontend (nginx)
├── .env.example                     all settings with comments
└── .github/workflows/ci.yml         tests, lint, build and Docker images on every push
```

---

## Design decisions

- **Strategy pattern for question types.** One `QuestionTypeHandler` per type handles validation and grading behind a registry, so adding a type touches no `switch` elsewhere.
- **JSONB for type-specific data.** One `question` table holds `content`, shown to learners, and `answer_key`, never sent before grading. Both map to sealed interfaces and records.
- **Server-side grading and shuffling.** Answers are graded on the server. Ordering and matching items are stored shuffled, so the display order never leaks the answer.
- **LLM output is never trusted.** Every generated question is validated by its type handler, and invalid ones are dropped.
- **Background generation jobs** run on virtual threads with progress polling, one job per user and a Bucket4j token-bucket limit. They fail fast when the model is unreachable instead of holding an HTTP request open for minutes.
- **Attempts are server-side state.** One open attempt per learner and quiz is enforced by a partial unique index. Answers autosave per question, and shuffling comes from a per-attempt seed, so a resumed attempt looks identical.
- **Quiz lifecycle protects results.** Questions are editable only in drafts. Quizzes that learners have taken are archived or duplicated, never edited or deleted.
- **Defence in depth for the library.** Built-in quizzes are blocked from editing and deleting in the service layer *and* by a PostgreSQL trigger. Their content lives in versioned JSON, validated by a unit test.
- **Account lifecycle enforced by the server.** The JWT filter reloads the user on every request, so a deactivation or access change applies to the very next click. A pending password change blocks every endpoint except changing the password. "Deactivated" is only revealed after a correct password, so emails can't be probed. People are deactivated, never deleted, so gradebooks stay intact.
- **Security.** Access tokens last 15 minutes and live only in memory. Refresh tokens rotate, are stored hashed, sit in an HttpOnly SameSite=Strict cookie and detect reuse. Permissions are checked by method security, resources you can't see return 404, CSV export is protected against formula injection, and containers run as non-root.
- **Aggregates in the database.** The gradebook and analytics use grouped JPQL with interface projections, so one query replaces one query per cell.
- **Schema managed by Flyway** with `ddl-auto: validate`. N+1 queries are avoided with entity graphs, batch fetching and denormalised counts.

---

## Testing

```bash
cd backend && ./mvnw test       # unit + integration tests (Docker must be running)
cd frontend && npm run build    # type-check and production build
cd frontend && npx oxlint src   # lint
```

- **Unit tests** cover the domain rules: grading for every question type, quiz policies, attempts, generation, account lifecycle and access rules.
- **Integration tests** boot the whole application on a real PostgreSQL with Testcontainers. They cover Flyway migrations, security filters and the main journeys from first login to gradebook.
- **CI** runs both test suites, the frontend lint and build, and the Docker image builds on every push.

> **Note for Windows contributors:** `@emnapi/*` are pinned as dev dependencies on purpose. Without them, `npm install` on Windows drops these Linux-only optional packages from `package-lock.json`, and `npm ci` then fails in Docker and CI.

---

## Known limitations

- Library quizzes must be copied into the Studio and published before they appear in a classroom's quiz list.
- Learners join classrooms with a join code; instructors can't add them from a list yet.
- After a password reset, a page already open on the person's device keeps working for up to 15 minutes, until its access token expires. Deactivation takes effect immediately.
- Rate limits are kept in memory, which suits a single server. Several servers would share them through Redis.
- Accounts are created by hand. There is no email invitation or bulk import yet.

---

## License

[MIT](LICENSE) © 2026 Harshal Rane
