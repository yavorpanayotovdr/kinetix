CREATE TABLE positions (
    portfolio_id        VARCHAR(255)   NOT NULL,
    instrument_id       VARCHAR(255)   NOT NULL,
    asset_class         VARCHAR(50)    NOT NULL,
    quantity            NUMERIC(28,12) NOT NULL,
    avg_cost_amount     NUMERIC(28,12) NOT NULL,
    market_price_amount NUMERIC(28,12) NOT NULL,
    currency            VARCHAR(3)     NOT NULL,
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_positions PRIMARY KEY (portfolio_id, instrument_id)
);
