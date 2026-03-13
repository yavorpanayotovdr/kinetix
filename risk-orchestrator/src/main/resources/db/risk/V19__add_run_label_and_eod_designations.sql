-- V19: Add run label, promotion fields, and official EOD designations table
-- Part of the Official EOD Run Labeling & Promotion workflow

-- Step 1: Suspend compression policy on valuation_jobs
SELECT remove_compression_policy('valuation_jobs', if_exists => true);

-- Step 2: Decompress all compressed chunks
DO $$
DECLARE chunk REGCLASS;
BEGIN
    FOR chunk IN
        SELECT format('%I.%I', c.chunk_schema, c.chunk_name)::regclass
        FROM timescaledb_information.chunks c
        WHERE c.hypertable_name = 'valuation_jobs'
          AND c.is_compressed = true
    LOOP
        PERFORM decompress_chunk(chunk);
    END LOOP;
END $$;

-- Step 3: Add new columns (nullable, no defaults for historical data)
ALTER TABLE valuation_jobs ADD COLUMN IF NOT EXISTS triggered_by VARCHAR(255) NULL;
ALTER TABLE valuation_jobs ADD COLUMN IF NOT EXISTS run_label VARCHAR(20) NULL;
ALTER TABLE valuation_jobs ADD COLUMN IF NOT EXISTS promoted_at TIMESTAMPTZ NULL;
ALTER TABLE valuation_jobs ADD COLUMN IF NOT EXISTS promoted_by VARCHAR(255) NULL;

-- Step 4: Create advisory partial index for query performance (not constraint enforcement)
CREATE INDEX IF NOT EXISTS idx_valuation_jobs_official_eod
    ON valuation_jobs (portfolio_id, valuation_date)
    WHERE run_label = 'OFFICIAL_EOD';

-- Step 5: Re-enable compression policy
SELECT add_compression_policy('valuation_jobs', INTERVAL '90 days', if_not_exists => true);

-- Step 6: Create official_eod_designations lookup table (standard PG table, not hypertable)
-- This table enforces the uniqueness constraint that one Official EOD per portfolio per date.
-- The PK constraint works correctly here (unlike on the TimescaleDB hypertable where
-- partial unique indexes cannot guarantee cross-chunk uniqueness).
CREATE TABLE IF NOT EXISTS official_eod_designations (
    portfolio_id   VARCHAR(255) NOT NULL,
    valuation_date DATE         NOT NULL,
    job_id         UUID         NOT NULL,
    promoted_at    TIMESTAMPTZ  NOT NULL,
    promoted_by    VARCHAR(255) NOT NULL,
    PRIMARY KEY (portfolio_id, valuation_date)
);
