-- Temporarily drop immutability triggers (rules were replaced by triggers in V4)
DROP TRIGGER IF EXISTS prevent_audit_update ON audit_events;
DROP TRIGGER IF EXISTS prevent_audit_delete ON audit_events;

-- Fix column types
ALTER TABLE audit_events
    ALTER COLUMN quantity TYPE NUMERIC(28,12) USING quantity::NUMERIC(28,12),
    ALTER COLUMN price_amount TYPE NUMERIC(28,12) USING price_amount::NUMERIC(28,12),
    ALTER COLUMN traded_at TYPE TIMESTAMPTZ USING traded_at::TIMESTAMPTZ;

-- Re-create immutability triggers
CREATE TRIGGER prevent_audit_update
    BEFORE UPDATE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_mutation();

CREATE TRIGGER prevent_audit_delete
    BEFORE DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_mutation();
