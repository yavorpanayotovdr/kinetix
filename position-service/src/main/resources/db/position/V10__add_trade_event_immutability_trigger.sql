-- Prevent mutation of core trade event fields.
-- Status changes (LIVE→ARCHIVED) are permitted for lifecycle transitions.
CREATE OR REPLACE FUNCTION prevent_trade_event_core_mutation() RETURNS trigger AS $$
BEGIN
    IF (OLD.trade_id     IS DISTINCT FROM NEW.trade_id     OR
        OLD.portfolio_id IS DISTINCT FROM NEW.portfolio_id OR
        OLD.instrument_id IS DISTINCT FROM NEW.instrument_id OR
        OLD.asset_class  IS DISTINCT FROM NEW.asset_class  OR
        OLD.side         IS DISTINCT FROM NEW.side         OR
        OLD.quantity     IS DISTINCT FROM NEW.quantity     OR
        OLD.price_amount IS DISTINCT FROM NEW.price_amount OR
        OLD.price_currency IS DISTINCT FROM NEW.price_currency OR
        OLD.traded_at    IS DISTINCT FROM NEW.traded_at    OR
        OLD.created_at   IS DISTINCT FROM NEW.created_at   OR
        OLD.event_type   IS DISTINCT FROM NEW.event_type) THEN
        RAISE EXCEPTION 'Core trade event fields are immutable';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_trade_event_core_mutation
    BEFORE UPDATE ON trade_events
    FOR EACH ROW EXECUTE FUNCTION prevent_trade_event_core_mutation();

-- Prevent deletion of trade events entirely.
CREATE OR REPLACE FUNCTION prevent_trade_event_deletion() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Trade events cannot be deleted';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_trade_event_deletion
    BEFORE DELETE ON trade_events
    FOR EACH ROW EXECUTE FUNCTION prevent_trade_event_deletion();
