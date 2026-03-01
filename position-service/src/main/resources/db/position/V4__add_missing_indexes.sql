CREATE INDEX IF NOT EXISTS idx_positions_instrument_id ON positions(instrument_id);
CREATE INDEX IF NOT EXISTS idx_positions_updated_at ON positions(updated_at);
