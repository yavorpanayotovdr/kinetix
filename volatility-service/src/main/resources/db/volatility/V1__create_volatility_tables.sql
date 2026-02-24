CREATE TABLE IF NOT EXISTS volatility_surfaces (
    instrument_id VARCHAR(255) NOT NULL,
    as_of_date    TIMESTAMPTZ  NOT NULL,
    source        VARCHAR(50)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (instrument_id, as_of_date)
);

CREATE INDEX idx_vol_surfaces_instrument ON volatility_surfaces (instrument_id);

CREATE TABLE IF NOT EXISTS volatility_surface_points (
    instrument_id VARCHAR(255) NOT NULL,
    as_of_date    TIMESTAMPTZ  NOT NULL,
    strike        DECIMAL(28, 12) NOT NULL,
    maturity_days INTEGER      NOT NULL,
    implied_vol   DECIMAL(18, 8)  NOT NULL,
    PRIMARY KEY (instrument_id, as_of_date, strike, maturity_days),
    FOREIGN KEY (instrument_id, as_of_date) REFERENCES volatility_surfaces(instrument_id, as_of_date)
);
