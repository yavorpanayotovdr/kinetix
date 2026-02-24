CREATE TABLE IF NOT EXISTS dividend_yields (
    instrument_id VARCHAR(255) NOT NULL,
    as_of_date    TIMESTAMPTZ  NOT NULL,
    yield         DECIMAL(18, 8) NOT NULL,
    ex_date       VARCHAR(10),
    source        VARCHAR(50)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (instrument_id, as_of_date)
);

CREATE INDEX idx_dividend_yields_instrument ON dividend_yields (instrument_id);

CREATE TABLE IF NOT EXISTS credit_spreads (
    instrument_id VARCHAR(255) NOT NULL,
    as_of_date    TIMESTAMPTZ  NOT NULL,
    spread        DECIMAL(18, 8) NOT NULL,
    rating        VARCHAR(20),
    source        VARCHAR(50)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (instrument_id, as_of_date)
);

CREATE INDEX idx_credit_spreads_instrument ON credit_spreads (instrument_id);
