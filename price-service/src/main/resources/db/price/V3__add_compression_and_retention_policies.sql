-- Enable compression on the prices hypertable.
-- Segment by instrument_id so that queries filtering on a single instrument
-- touch only the relevant compressed segments; order by timestamp so that
-- range scans within a segment are sequential.
ALTER TABLE prices SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'instrument_id',
    timescaledb.compress_orderby = 'timestamp DESC'
);

-- Compress chunks older than 30 days (runs automatically on the job scheduler).
SELECT add_compression_policy('prices', INTERVAL '30 days');

-- Drop raw chunks older than 2 years to bound storage growth.
SELECT add_retention_policy('prices', INTERVAL '2 years');
