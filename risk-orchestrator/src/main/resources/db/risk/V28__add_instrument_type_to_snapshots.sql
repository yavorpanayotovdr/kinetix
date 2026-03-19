ALTER TABLE daily_risk_snapshots ADD COLUMN instrument_type VARCHAR(32);

CREATE INDEX idx_snapshots_instrument_type ON daily_risk_snapshots (instrument_type) WHERE instrument_type IS NOT NULL;
