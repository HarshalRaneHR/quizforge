-- V8: built-in library quizzes (seeded from JSON at startup). They can never be deleted —
-- enforced by the API and, as a last line of defence, by this trigger in the database itself.

ALTER TABLE quiz ADD COLUMN built_in_slug VARCHAR(80);
ALTER TABLE quiz ADD CONSTRAINT uk_quiz_built_in_slug UNIQUE (built_in_slug);

CREATE FUNCTION prevent_built_in_quiz_delete() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Built-in quiz "%" cannot be deleted', OLD.built_in_slug
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_built_in_quiz_delete
    BEFORE DELETE ON quiz
    FOR EACH ROW
    WHEN (OLD.built_in_slug IS NOT NULL)
    EXECUTE FUNCTION prevent_built_in_quiz_delete();
