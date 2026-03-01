-- Enable TimescaleDB extension (idempotent).
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- TimescaleDB requires the partitioning column (received_at) to be part of
-- every unique constraint. Replace the single-column PK with a composite one.
ALTER TABLE audit_events DROP CONSTRAINT pk_audit_events;
ALTER TABLE audit_events ADD CONSTRAINT pk_audit_events PRIMARY KEY (id, received_at);

-- Drop rules that are incompatible with hypertables; replaced by triggers below.
DROP RULE IF EXISTS prevent_update_audit ON audit_events;
DROP RULE IF EXISTS prevent_delete_audit ON audit_events;

-- Convert to a hypertable partitioned on received_at.
SELECT create_hypertable('audit_events', 'received_at', migrate_data => true);

-- Re-add immutability protection using triggers (supported by hypertables).
CREATE OR REPLACE FUNCTION prevent_audit_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Audit events are immutable and cannot be modified or deleted';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER prevent_audit_update
    BEFORE UPDATE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_mutation();

CREATE TRIGGER prevent_audit_delete
    BEFORE DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_mutation();

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
