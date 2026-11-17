# QuizForge — AI Quiz & Learning Tracker

Paste study material → get an AI-generated multiple-choice quiz → take it → see your score with explanations → track your history.

**Stack:** React + TypeScript (Vite, TanStack Query, React Router) · Spring Boot 4 · Java 25 · Spring Security + JWT · Spring Data JPA / Hibernate · PostgreSQL 16 · Spring AI (Ollama) · JUnit 5 + Mockito · Docker Compose

## Architecture

```
┌──────────────────────────┐       /api/*        ┌─────────────────────────────┐
│ React + TS (Vite)        │ ──────────────────▶ │ Spring Boot (port 8080)     │
│ TanStack Query, Router   │   Bearer <JWT>      │ Security (JWT filter)       │
│ nginx in Docker (:3000)  │ ◀────────────────── │ Controllers → Services      │
└──────────────────────────┘        JSON         │ Spring Data JPA ─┐          │
                                                 │ Spring AI ───────┼──▶ Ollama│
                                                 └──────────────────┼──────────┘
                                                                    ▼
                                                           PostgreSQL 16
```

## Run locally

Prerequisites: Docker Desktop, [Ollama](https://ollama.com) with `ollama pull llama3.1`.

```bash
cp .env.example .env      # fill in JWT_SECRET (Base64, 32+ bytes) and admin credentials
docker compose up --build
```

Open http://localhost:3000.

### Development mode

```bash
docker compose up -d db                 # PostgreSQL only
cd backend && ./mvnw spring-boot:run    # needs JWT_SECRET, ADMIN_EMAIL, ADMIN_PASSWORD env vars
cd frontend && npm install && npm run dev   # http://localhost:5173, proxies /api to :8080
```

Run backend tests: `cd backend && ./mvnw test`

## API

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/auth/register` | public | Create account, returns JWT |
| POST | `/api/auth/login` | public | Login, returns JWT |
| POST | `/api/quizzes/generate` | user | AI-generate and save a quiz |
| GET | `/api/quizzes?page=&size=` | user | My quizzes (paginated) |
| GET | `/api/quizzes/{id}` | owner | Quiz to take (no answers included) |
| DELETE | `/api/quizzes/{id}` | owner or ADMIN | Delete quiz + its attempts |
| POST | `/api/quizzes/{id}/attempts` | owner | Submit answers, get score + explanations |
| GET | `/api/attempts/me?page=&size=` | user | My attempt history |
| GET | `/api/admin/users?page=&size=` | ADMIN | List all users |

## Design decisions

- **Server-side grading:** correct answers are never sent to the browser before submission.
- **No DB connection held during the LLM call:** quiz generation isn't `@Transactional`; only the save is.
- **LLM output is validated** (4 options, index in range) before it touches the database; failures return HTTP 502 and save nothing.
- **N+1 avoided** with a stored `questionCount`, `@EntityGraph` and `@BatchSize`; `open-in-view` is off.
- **404 instead of 403** for other users' quizzes, so quiz IDs can't be enumerated.
- **RFC 7807 `ProblemDetail`** error responses via a global exception handler.
