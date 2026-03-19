-- Standardize all identifier columns to VARCHAR(255) to prevent
-- constraint violations when an ID exceeds the narrower width.

-- daily_risk_snapshots is a TimescaleDB hypertable; decompress any
-- compressed chunks before altering the column type, then recompress.
SELECT decompress_chunk(c.chunk_name::regclass) FROM timescaledb_information.chunks c
WHERE c.hypertable_name = 'daily_risk_snapshots' AND c.is_compressed = true;

ALTER TABLE daily_risk_snapshots
    ALTER COLUMN instrument_id TYPE VARCHAR(255);

-- Recompress any chunks that were decompressed above.
SELECT compress_chunk(c.chunk_name::regclass) FROM timescaledb_information.chunks c
WHERE c.hypertable_name = 'daily_risk_snapshots' AND c.is_compressed = false;

-- sod_baselines: portfolio_id VARCHAR(64) → VARCHAR(255)
ALTER TABLE sod_baselines
    ALTER COLUMN portfolio_id TYPE VARCHAR(255);
