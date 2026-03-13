-- V20: Add market_data_snapshot_id to valuation_jobs for reproducibility
-- This column records the opaque identifier of the market data snapshot
-- used in each calculation, enabling exact reproduction of results.

-- Suspend compression policy before altering hypertable
SELECT remove_compression_policy('valuation_jobs', if_exists => true);

-- Decompress all compressed chunks
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

ALTER TABLE valuation_jobs
    ADD COLUMN IF NOT EXISTS market_data_snapshot_id VARCHAR(255) NULL;

-- Re-enable compression policy (90-day)
SELECT add_compression_policy('valuation_jobs', INTERVAL '90 days', if_not_exists => true);
