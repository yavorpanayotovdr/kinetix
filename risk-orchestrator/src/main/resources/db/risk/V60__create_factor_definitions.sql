-- V60: Persist factor definitions — the five systematic risk factors whose
-- names and proxy instruments were previously hardcoded in the Python
-- risk engine (factor_model.py).

CREATE TABLE factor_definitions (
    factor_name        VARCHAR(64)  PRIMARY KEY,
    proxy_instrument_id VARCHAR(64) NOT NULL,
    description        TEXT         NOT NULL
);

INSERT INTO factor_definitions (factor_name, proxy_instrument_id, description) VALUES
    ('EQUITY_BETA',    'IDX-SPX', 'Equity market beta — proxy: S&P 500'),
    ('RATES_DURATION', 'US10Y',   'Rates duration — proxy: 10-year UST yield'),
    ('CREDIT_SPREAD',  'CDX-IG',  'Credit spread — proxy: IG credit index'),
    ('FX_DELTA',       'EURUSD',  'FX delta — proxy: EUR/USD'),
    ('VOL_EXPOSURE',   'VIX',     'Volatility exposure — proxy: VIX');
