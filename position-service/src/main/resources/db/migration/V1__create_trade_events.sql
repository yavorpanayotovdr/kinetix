CREATE TABLE trade_events (
    trade_id       VARCHAR(255)   NOT NULL,
    portfolio_id   VARCHAR(255)   NOT NULL,
    instrument_id  VARCHAR(255)   NOT NULL,
    asset_class    VARCHAR(50)    NOT NULL,
    side           VARCHAR(10)    NOT NULL,
    quantity       NUMERIC(28,12) NOT NULL,
    price_amount   NUMERIC(28,12) NOT NULL,
    price_currency VARCHAR(3)     NOT NULL,
    traded_at      TIMESTAMPTZ    NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_trade_events PRIMARY KEY (trade_id)
);

CREATE INDEX idx_trade_events_portfolio ON trade_events (portfolio_id);
CREATE INDEX idx_trade_events_traded_at ON trade_events (traded_at);
