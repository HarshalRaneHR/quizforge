-- Invite-only accounts: names, deactivation, forced password change and last login.

ALTER TABLE users ADD COLUMN full_name VARCHAR(80);
ALTER TABLE users ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE users ADD CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'DEACTIVATED'));
-- Set for accounts an admin created or reset: the user must choose their own password first.
ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN last_login_at TIMESTAMPTZ;

-- The built-in library's owner is a system account nobody logs in as.
UPDATE users SET full_name = 'QuizForge Library', status = 'DEACTIVATED' WHERE email = 'library@quizforge.app';

-- Sessions are now also ended by a password change, an admin reset or a deactivation.
ALTER TABLE refresh_tokens DROP CONSTRAINT ck_refresh_tokens_reason;
ALTER TABLE refresh_tokens ADD CONSTRAINT ck_refresh_tokens_reason
    CHECK (revoke_reason IN ('ROTATED', 'LOGOUT', 'REUSE_DETECTED', 'PASSWORD_CHANGED', 'PASSWORD_RESET', 'DEACTIVATED'));
