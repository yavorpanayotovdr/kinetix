-- Make trade-specific columns nullable to support governance audit events.
-- Governance events (model transitions, scenario approvals, limit breaches, etc.)
-- do not have trade data; making these nullable is backward compatible because
-- existing trade events will continue to have values in all these columns.

-- Drop immutability triggers so we can alter the table.
DROP TRIGGER IF EXISTS prevent_audit_update ON audit_events;
DROP TRIGGER IF EXISTS prevent_audit_delete ON audit_events;

ALTER TABLE audit_events
    ALTER COLUMN trade_id      DROP NOT NULL,
    ALTER COLUMN book_id       DROP NOT NULL,
    ALTER COLUMN instrument_id DROP NOT NULL,
    ALTER COLUMN asset_class   DROP NOT NULL,
    ALTER COLUMN side          DROP NOT NULL,
    ALTER COLUMN quantity      DROP NOT NULL,
    ALTER COLUMN price_amount  DROP NOT NULL,
    ALTER COLUMN price_currency DROP NOT NULL,
    ALTER COLUMN traded_at     DROP NOT NULL;

-- Add governance-specific columns.
ALTER TABLE audit_events
    ADD COLUMN IF NOT EXISTS model_name    VARCHAR(255),
    ADD COLUMN IF NOT EXISTS scenario_id  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS limit_id     VARCHAR(255),
    ADD COLUMN IF NOT EXISTS submission_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS details      TEXT;

-- Re-create immutability triggers.
CREATE TRIGGER prevent_audit_update
    BEFORE UPDATE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_mutation();

CREATE TRIGGER prevent_audit_delete
    BEFORE DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_mutation();
