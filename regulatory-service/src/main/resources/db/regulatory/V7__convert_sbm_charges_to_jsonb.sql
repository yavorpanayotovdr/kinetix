-- Convert SBM charges from TEXT to JSONB for queryability and validation.
-- frtb_calculations is a hypertable — decompress chunks before altering.

SELECT decompress_chunk(c.chunk_name::regclass)
FROM timescaledb_information.chunks c
WHERE c.hypertable_name = 'frtb_calculations' AND c.is_compressed = true;

ALTER TABLE frtb_calculations
    ALTER COLUMN sbm_charges_json TYPE JSONB USING sbm_charges_json::JSONB;

-- Re-enable compression on old chunks
SELECT compress_chunk(c.chunk_name::regclass)
FROM timescaledb_information.chunks c
WHERE c.hypertable_name = 'frtb_calculations' AND c.is_compressed = false
  AND c.range_end < NOW() - INTERVAL '90 days';
