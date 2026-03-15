-- V27 rollback: Rename phases back to steps, drop current_phase
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

ALTER TABLE valuation_jobs DROP COLUMN IF EXISTS current_phase;
ALTER TABLE valuation_jobs RENAME COLUMN phases TO steps;

SELECT add_compression_policy('valuation_jobs', INTERVAL '90 days', if_not_exists => true);
