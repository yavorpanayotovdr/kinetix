-- Trade lifecycle columns
ALTER TABLE trade_events ADD COLUMN trade_type VARCHAR(10) NOT NULL DEFAULT 'NEW';
ALTER TABLE trade_events ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'LIVE';
ALTER TABLE trade_events ADD COLUMN original_trade_id VARCHAR(255);

CREATE INDEX idx_trade_events_original ON trade_events (original_trade_id);

-- Realized P&L on positions
ALTER TABLE positions ADD COLUMN realized_pnl_amount NUMERIC(28,12) NOT NULL DEFAULT 0;
