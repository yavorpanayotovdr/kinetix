-- Standardize all identifier columns to VARCHAR(255) to prevent
-- constraint violations when an ID exceeds the narrower width.

-- daily_risk_snapshots is a TimescaleDB hypertable; decompress any
-- compressed chunks before altering the column type, then recompress.
SELECT decompress_chunk(c) FROM show_chunks('daily_risk_snapshots') c
WHERE is_compressed;

ALTER TABLE daily_risk_snapshots
    ALTER COLUMN instrument_id TYPE VARCHAR(255);

-- Recompress any chunks that were decompressed above.
SELECT compress_chunk(c) FROM show_chunks('daily_risk_snapshots') c
WHERE NOT is_compressed;

-- sod_baselines: portfolio_id VARCHAR(64) → VARCHAR(255)
ALTER TABLE sod_baselines
    ALTER COLUMN portfolio_id TYPE VARCHAR(255);
