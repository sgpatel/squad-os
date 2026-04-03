-- Widen topic column from VARCHAR(100) to VARCHAR(500)
-- LLMs sometimes return multiple topics separated by / or ,
-- The service layer truncates to 100 chars, but this migration
-- adds a safety net and future-proofs the schema.
-- Also widen other potentially long fields.
ALTER TABLE mentions ALTER COLUMN topic          TYPE VARCHAR(500);
ALTER TABLE mentions ALTER COLUMN escalation_path TYPE VARCHAR(500);
ALTER TABLE mentions ALTER COLUMN summary        TYPE VARCHAR(1000);
