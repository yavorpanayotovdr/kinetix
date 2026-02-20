CREATE EXTENSION IF NOT EXISTS timescaledb;

CREATE TABLE market_data (
    instrument_id  VARCHAR(255)   NOT NULL,
    price_amount   NUMERIC(28,12) NOT NULL,
    price_currency VARCHAR(3)     NOT NULL,
    timestamp      TIMESTAMPTZ    NOT NULL,
    source         VARCHAR(50)    NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_market_data PRIMARY KEY (instrument_id, timestamp)
);

SELECT create_hypertable('market_data', 'timestamp');

CREATE INDEX idx_market_data_instrument ON market_data (instrument_id, timestamp DESC);
