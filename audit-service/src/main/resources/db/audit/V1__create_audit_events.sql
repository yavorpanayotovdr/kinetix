CREATE TABLE audit_events (
    id             BIGSERIAL      NOT NULL,
    trade_id       VARCHAR(255)   NOT NULL,
    portfolio_id   VARCHAR(255)   NOT NULL,
    instrument_id  VARCHAR(255)   NOT NULL,
    asset_class    VARCHAR(50)    NOT NULL,
    side           VARCHAR(10)    NOT NULL,
    quantity       VARCHAR(50)    NOT NULL,
    price_amount   VARCHAR(50)    NOT NULL,
    price_currency VARCHAR(3)     NOT NULL,
    traded_at      VARCHAR(50)    NOT NULL,
    received_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_audit_events PRIMARY KEY (id)
);

CREATE INDEX idx_audit_events_portfolio ON audit_events (portfolio_id);
CREATE INDEX idx_audit_events_trade_id ON audit_events (trade_id);
