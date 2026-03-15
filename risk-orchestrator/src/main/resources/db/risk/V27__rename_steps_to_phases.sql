-- Suspend compression policy for DDL on hypertable
SELECT remove_compression_policy('valuation_jobs', if_exists => true);

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

ALTER TABLE valuation_jobs RENAME COLUMN steps TO phases;
ALTER TABLE valuation_jobs ADD COLUMN IF NOT EXISTS current_phase VARCHAR(50) NULL;

SELECT add_compression_policy('valuation_jobs', INTERVAL '90 days', if_not_exists => true);
