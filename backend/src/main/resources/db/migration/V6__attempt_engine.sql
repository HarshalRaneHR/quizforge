-- V6: quiz settings and an attempt lifecycle (start → autosave → submit), with resume.

ALTER TABLE quiz
    ADD COLUMN max_attempts       INTEGER,
    ADD COLUMN pass_percentage    INTEGER,
    ADD COLUMN shuffle_questions  BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN shuffle_options    BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN results_visibility VARCHAR(20) NOT NULL DEFAULT 'FULL',
    ADD CONSTRAINT ck_quiz_max_attempts CHECK (max_attempts BETWEEN 1 AND 100),
    ADD CONSTRAINT ck_quiz_pass_percentage CHECK (pass_percentage BETWEEN 1 AND 100),
    ADD CONSTRAINT ck_quiz_results_visibility CHECK (results_visibility IN ('FULL', 'SCORE_ONLY'));

-- Existing attempts were submitted in one go.
ALTER TABLE attempt
    ADD COLUMN status       VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    ADD COLUMN started_at   TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN submitted_at TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN shuffle_seed BIGINT      NOT NULL DEFAULT 0,
    ADD COLUMN passed       BOOLEAN,
    ADD CONSTRAINT ck_attempt_status CHECK (status IN ('IN_PROGRESS', 'SUBMITTED'));
UPDATE attempt SET started_at = created_at, submitted_at = created_at;
ALTER TABLE attempt ALTER COLUMN started_at SET NOT NULL;

-- At most ONE open attempt per learner per quiz, enforced by the database (no race on double "Start").
CREATE UNIQUE INDEX uk_attempt_one_open ON attempt (quiz_id, user_id) WHERE status = 'IN_PROGRESS';
CREATE INDEX idx_attempt_quiz_user ON attempt (quiz_id, user_id);

-- Answers are now saved while the attempt is open (autosave), one row per question.
ALTER TABLE attempt_answer
    ADD COLUMN flagged    BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN updated_at TIMESTAMP(6) WITH TIME ZONE,
    ADD CONSTRAINT uk_attempt_answer_question UNIQUE (attempt_id, question_id);
