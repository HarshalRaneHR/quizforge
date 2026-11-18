-- V9: per-user permission overrides on top of the role's defaults.
-- granted = true adds the permission, false removes it. Only "adjustable" permissions are allowed
-- (enforced in the service); admin-only powers always come from the ADMIN role.

CREATE TABLE user_permission_override (
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    permission VARCHAR(40) NOT NULL,
    granted    BOOLEAN     NOT NULL,
    PRIMARY KEY (user_id, permission),
    CONSTRAINT ck_override_permission CHECK (permission IN ('QUIZ_GENERATE', 'QUIZ_AUTHOR', 'CLASSROOM_MANAGE'))
);
