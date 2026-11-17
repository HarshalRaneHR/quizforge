-- V5: quiz lifecycle (draft → published → archived), visibility, and background AI generation jobs.

ALTER TABLE quiz
    ADD COLUMN status       VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
    ADD COLUMN visibility   VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    ADD COLUMN description  TEXT,
    ADD COLUMN published_at TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN updated_at   TIMESTAMP(6) WITH TIME ZONE,
    ALTER COLUMN source_text DROP NOT NULL,   -- authored quizzes may mix several sources
    ADD CONSTRAINT ck_quiz_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    ADD CONSTRAINT ck_quiz_visibility CHECK (visibility IN ('PRIVATE', 'PUBLIC'));

-- Existing quizzes were personal practice quizzes: published, private.
UPDATE quiz SET published_at = created_at, updated_at = created_at;

-- Catalog query: published public quizzes, newest first.
CREATE INDEX idx_quiz_catalog ON quiz (status, visibility, published_at DESC);

CREATE TABLE generation_job (
    id            UUID PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    quiz_id       BIGINT       REFERENCES quiz (id) ON DELETE SET NULL,
    target        VARCHAR(20)  NOT NULL,   -- NEW_PRACTICE_QUIZ or APPEND_TO_QUIZ
    status        VARCHAR(20)  NOT NULL,
    request       JSONB        NOT NULL,
    total_steps   INTEGER      NOT NULL,
    done_steps    INTEGER      NOT NULL DEFAULT 0,
    question_count INTEGER     NOT NULL DEFAULT 0,
    error         TEXT,
    created_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    finished_at   TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT ck_job_target CHECK (target IN ('NEW_PRACTICE_QUIZ', 'APPEND_TO_QUIZ')),
    CONSTRAINT ck_job_status CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED'))
);
CREATE INDEX idx_generation_job_user_status ON generation_job (user_id, status);
