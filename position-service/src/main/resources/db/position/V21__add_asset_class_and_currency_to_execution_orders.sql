-- V21: Add asset_class and currency to execution_orders so that FIX fills can book
-- trades with the correct asset class and settlement currency instead of hardcoded defaults.
-- Existing rows default to EQUITY / USD, matching the previous hardcoded behaviour.

ALTER TABLE execution_orders
    ADD COLUMN asset_class VARCHAR(30)  NOT NULL DEFAULT 'EQUITY',
    ADD COLUMN currency    VARCHAR(10)  NOT NULL DEFAULT 'USD';
