-- Fix: V10 trigger references OLD.portfolio_id but V13 renamed the column to book_id.
-- Recreate the function with the correct column name.
CREATE OR REPLACE FUNCTION prevent_trade_event_core_mutation() RETURNS trigger AS $$
BEGIN
    IF (OLD.trade_id     IS DISTINCT FROM NEW.trade_id     OR
        OLD.book_id      IS DISTINCT FROM NEW.book_id      OR
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
