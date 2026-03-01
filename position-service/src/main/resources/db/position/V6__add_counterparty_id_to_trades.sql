ALTER TABLE trade_events ADD COLUMN counterparty_id VARCHAR(255);

CREATE INDEX idx_trade_events_counterparty ON trade_events(counterparty_id);
