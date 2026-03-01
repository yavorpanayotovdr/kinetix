-- Temporarily drop immutability rules
DROP RULE IF EXISTS prevent_update_audit ON audit_events;
DROP RULE IF EXISTS prevent_delete_audit ON audit_events;

-- Fix column types
ALTER TABLE audit_events
    ALTER COLUMN quantity TYPE NUMERIC(28,12) USING quantity::NUMERIC(28,12),
    ALTER COLUMN price_amount TYPE NUMERIC(28,12) USING price_amount::NUMERIC(28,12),
    ALTER COLUMN traded_at TYPE TIMESTAMPTZ USING traded_at::TIMESTAMPTZ;

-- Re-create immutability rules
CREATE RULE prevent_update_audit AS ON UPDATE TO audit_events DO INSTEAD NOTHING;
CREATE RULE prevent_delete_audit AS ON DELETE TO audit_events DO INSTEAD NOTHING;
