# QuizForge — AI Quiz & Assessment Platform

Instructors build quizzes with AI and publish them; learners explore, take them and track progress. Learners can also turn their own notes into practice quizzes.

**Stack:** React 19 + TypeScript (Vite, Tailwind CSS, shadcn/ui, TanStack Query, React Router) · Spring Boot 4 · Java 25 · Spring Security (JWT + refresh tokens) · Spring Data JPA / Hibernate · PostgreSQL 16 + Flyway · Spring AI (Ollama) · springdoc OpenAPI · JUnit 5 + Mockito · Docker Compose

## Features

- **7 question types** — multiple choice, multi-select, true/false, fill in the blank, numeric, ordering, matching — each with its own validation and grading, including **partial credit**
- **AI generation from a blueprint** — choose the number of questions, split them across types (by hand, *Equal*, *Random* or presets) and pick a difficulty; runs as a **background job** with live progress
- **Studio for instructors** — create drafts, edit every question in a per-type editor, reorder, generate more with AI, then **publish → archive / restore / duplicate**
- **Explore** — learners find published quizzes and take them; answer keys never reach the browser before grading
- **Roles** — Learner, Instructor, Admin, built from fine-grained permissions; admins create users and change roles
- **Progress** — attempt history with scores and averages

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

Prerequisites: Docker Desktop, [Ollama](https://ollama.com) with `ollama pull llama3.1`.

```bash
cp .env.example .env      # fill in JWT_SECRET (Base64, 32+ bytes), DB and admin credentials
docker compose up --build
```

Open http://localhost:3000 and log in with the admin account from `.env`. Create an instructor under **Administration → Users**.

### Development mode

```bash
docker compose up -d db                       # or use a local PostgreSQL (see .env)
cd backend && ./mvnw spring-boot:run          # reads ../.env automatically
cd frontend && npm install && npm run dev     # http://localhost:5173, proxies /api to :8080
```

- Backend tests: `cd backend && ./mvnw test`
- Regenerate frontend API types after backend changes (backend running): `cd frontend && npm run gen:api`

## API

Interactive docs (Swagger UI): **http://localhost:8080/swagger-ui.html** · OpenAPI spec: `/v3/api-docs`.
To try protected endpoints: call `POST /api/auth/login`, copy `token`, click **Authorize** and paste it.
Disable in production with `API_DOCS_ENABLED=false`.

| Area | Endpoints |
|---|---|
| Auth | `POST /api/auth/register · login · refresh · logout`, `GET /api/me` |
| Practice & catalog | `POST /api/quizzes/generate` (background job), `GET /api/generation-jobs/{id}`, `GET /api/quizzes`, `GET /api/quizzes/explore` |
| Taking quizzes | `GET /api/quizzes/{id}`, `POST /api/quizzes/{id}/attempts`, `GET /api/attempts/me`, `DELETE /api/quizzes/{id}` |
| Studio (instructor) | `/api/studio/quizzes` — create, edit, questions CRUD + reorder, AI generate, publish / unpublish / archive / restore / duplicate |
| Admin | `GET/POST /api/admin/users`, `PATCH /api/admin/users/{id}/role` |

## Design decisions

- **Strategy pattern for question types:** one `QuestionTypeHandler` per type (validation + grading) behind a registry; adding a type touches no `switch` elsewhere.
- **JSONB for type-specific data:** one `question` table with `content` (shown to learners) and `answer_key` (never sent before grading), mapped to sealed interfaces + records.
- **Server-side grading and shuffling:** answers are graded on the server; ordering/matching items are stored shuffled so the display order never leaks the answer.
- **LLM output is never trusted:** every generated question is validated by its type handler; invalid ones are dropped.
- **Background generation jobs** on virtual threads with progress polling, one job per user, and fail-fast when the model is unreachable — no HTTP request held open for minutes.
- **Quiz lifecycle protects results:** questions are editable only in DRAFT; quizzes learners have taken are archived or duplicated, never edited or deleted.
- **Security:** 15-minute access tokens in memory, rotating refresh tokens in an HttpOnly SameSite=Strict cookie (stored hashed, reuse detection), permission-based method security, 404 instead of 403 for resources you can't see.
- **Schema managed by Flyway** (`ddl-auto: validate`), N+1 avoided with entity graphs and denormalised counts.

## License

MIT
