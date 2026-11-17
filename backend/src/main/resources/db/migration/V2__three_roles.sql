-- V2: replace USER/ADMIN with ADMIN/INSTRUCTOR/LEARNER. Existing USER accounts become LEARNER.

ALTER TABLE users DROP CONSTRAINT ck_users_role;

UPDATE users SET role = 'LEARNER' WHERE role = 'USER';

ALTER TABLE users
    ADD CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'INSTRUCTOR', 'LEARNER'));
