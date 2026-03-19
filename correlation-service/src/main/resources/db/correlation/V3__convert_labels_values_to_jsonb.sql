-- Convert labels and values from TEXT to JSONB for schema validation.
-- The generated column labels_hash depends on the labels column expression
-- md5(labels), which requires a text argument. We must drop it before altering
-- the column type and recreate it with an explicit cast.
DROP INDEX IF EXISTS idx_correlation_hash_window;

ALTER TABLE correlation_matrices
    DROP COLUMN labels_hash;

ALTER TABLE correlation_matrices
    ALTER COLUMN labels TYPE JSONB USING labels::JSONB,
    ALTER COLUMN "values" TYPE JSONB USING "values"::JSONB;

ALTER TABLE correlation_matrices
    ADD COLUMN labels_hash VARCHAR(32) GENERATED ALWAYS AS (md5(labels::text)) STORED;

CREATE INDEX idx_correlation_hash_window ON correlation_matrices (labels_hash, window_days);
