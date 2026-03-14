-- Index to support the blob retention cleanup query which filters by created_at
CREATE INDEX IF NOT EXISTS idx_rmdb_created_at
    ON run_market_data_blobs (created_at);
