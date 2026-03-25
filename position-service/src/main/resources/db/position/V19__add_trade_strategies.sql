CREATE TABLE trade_strategies (
    strategy_id  VARCHAR(36)  PRIMARY KEY,
    book_id      VARCHAR(255) NOT NULL,
    strategy_type VARCHAR(50) NOT NULL,
    name         VARCHAR(255),
    created_at   TIMESTAMPTZ  NOT NULL
);

ALTER TABLE trade_events
    ADD COLUMN strategy_id VARCHAR(36) REFERENCES trade_strategies(strategy_id);

CREATE INDEX idx_trade_strategies_book_id ON trade_strategies(book_id);
CREATE INDEX idx_trade_events_strategy_id ON trade_events(strategy_id);
