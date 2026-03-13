-- V19 Rollback: Remove run label, promotion fields, and official EOD designations table
-- WARNING: This is NOT Flyway-managed. Execute manually in a maintenance window.

-- Step 1: Drop the lookup table
DROP TABLE IF EXISTS official_eod_designations;

-- Step 2: Drop the advisory index
DROP INDEX IF EXISTS idx_valuation_jobs_official_eod;

-- Step 3: Suspend compression before altering hypertable
SELECT remove_compression_policy('valuation_jobs', if_exists => true);

DO $$
DECLARE
    chunk RECORD;
BEGIN
    FOR chunk IN
        SELECT show_chunks.chunk_schema || '.' || show_chunks.chunk_name AS chunk_full_name
        FROM show_chunks('valuation_jobs')
        WHERE is_compressed
    LOOP
        EXECUTE format('SELECT decompress_chunk(%L)', chunk.chunk_full_name);
    END LOOP;
END $$;

-- Step 4: Drop columns
ALTER TABLE valuation_jobs DROP COLUMN IF EXISTS triggered_by;
ALTER TABLE valuation_jobs DROP COLUMN IF EXISTS run_label;
ALTER TABLE valuation_jobs DROP COLUMN IF EXISTS promoted_at;
ALTER TABLE valuation_jobs DROP COLUMN IF EXISTS promoted_by;

-- Step 5: Re-enable compression policy
SELECT add_compression_policy('valuation_jobs', INTERVAL '90 days', if_not_exists => true);

-- Step 6: Remove Flyway migration record
DELETE FROM flyway_schema_history WHERE version = '19';
