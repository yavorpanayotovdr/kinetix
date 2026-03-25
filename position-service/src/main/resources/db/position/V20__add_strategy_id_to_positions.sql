ALTER TABLE positions
    ADD COLUMN strategy_id VARCHAR(36) REFERENCES trade_strategies(strategy_id);

CREATE INDEX idx_positions_strategy_id ON positions(strategy_id);
