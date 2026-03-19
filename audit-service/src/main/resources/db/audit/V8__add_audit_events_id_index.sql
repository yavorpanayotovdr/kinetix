-- Enable efficient point lookups by id without requiring received_at.
-- The composite PK (id, received_at) is required by TimescaleDB but
-- does not support single-column id lookups.
CREATE UNIQUE INDEX IF NOT EXISTS idx_audit_events_id ON audit_events (id);
