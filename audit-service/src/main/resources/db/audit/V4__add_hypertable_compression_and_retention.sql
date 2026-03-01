-- Enable TimescaleDB extension (idempotent).
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- TimescaleDB requires the partitioning column (received_at) to be part of
-- every unique constraint. Replace the single-column PK with a composite one.
ALTER TABLE audit_events DROP CONSTRAINT pk_audit_events;
ALTER TABLE audit_events ADD CONSTRAINT pk_audit_events PRIMARY KEY (id, received_at);

-- Convert to a hypertable partitioned on received_at.
SELECT create_hypertable('audit_events', 'received_at', migrate_data => true);

-- Compress audit chunks older than 30 days to reduce storage.
-- TimescaleDB compression operates below the SQL rule layer, so the
-- prevent_update/prevent_delete rules do not interfere.
ALTER TABLE audit_events SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'portfolio_id',
    timescaledb.compress_orderby = 'received_at DESC'
);

SELECT add_compression_policy('audit_events', INTERVAL '30 days');

-- Regulatory requirement: retain audit events for 7 years.
SELECT add_retention_policy('audit_events', INTERVAL '7 years');
