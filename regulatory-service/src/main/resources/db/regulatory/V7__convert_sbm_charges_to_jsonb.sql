-- Convert SBM charges from TEXT to JSONB for queryability and validation.
-- frtb_calculations is a hypertable — decompress chunks before altering.

SELECT decompress_chunk(c) FROM show_chunks('frtb_calculations') c
WHERE is_compressed;

ALTER TABLE frtb_calculations
    ALTER COLUMN sbm_charges_json TYPE JSONB USING sbm_charges_json::JSONB;

-- Re-enable compression on old chunks
SELECT compress_chunk(c) FROM show_chunks('frtb_calculations') c
WHERE NOT is_compressed
  AND range_end < NOW() - INTERVAL '90 days';
