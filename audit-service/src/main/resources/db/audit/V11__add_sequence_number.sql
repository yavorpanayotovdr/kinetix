-- Add an application-assigned sequence number to audit events for gap detection.
-- The sequence is assigned by the application (not a DB sequence) so it can be
-- set explicitly during DLQ replay without collisions. A unique constraint
-- enforces monotonicity and allows gap queries.

-- Drop immutability triggers so we can alter the table.
DROP TRIGGER IF EXISTS prevent_audit_update ON audit_events;
DROP TRIGGER IF EXISTS prevent_audit_delete ON audit_events;

ALTER TABLE audit_events
    ADD COLUMN IF NOT EXISTS sequence_number BIGINT;

CREATE UNIQUE INDEX IF NOT EXISTS idx_audit_events_seq ON audit_events (sequence_number, received_at)
    WHERE sequence_number IS NOT NULL;

-- Re-create immutability triggers.
CREATE TRIGGER prevent_audit_update
    BEFORE UPDATE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_mutation();

CREATE TRIGGER prevent_audit_delete
    BEFORE DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_mutation();
