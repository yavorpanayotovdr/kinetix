-- Stores average daily volume (ADV) and bid-ask spread data per instrument.
-- This is reference data used to classify positions into liquidity tiers
-- and compute liquidity-adjusted VaR in the risk pipeline.
--
-- adv: average daily volume in instrument currency
-- bid_ask_spread_bps: typical bid-ask spread in basis points
-- adv_updated_at: timestamp of the most recent ADV observation
--   (used to flag staleness: > 2 days old -> stale)
CREATE TABLE instrument_liquidity (
    instrument_id       VARCHAR(255)  NOT NULL,
    adv                 NUMERIC(24,6) NOT NULL,
    bid_ask_spread_bps  NUMERIC(10,4) NOT NULL DEFAULT 0,
    asset_class         VARCHAR(50)   NOT NULL,
    adv_updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_instrument_liquidity PRIMARY KEY (instrument_id)
);

CREATE INDEX idx_instrument_liquidity_asset_class ON instrument_liquidity (asset_class);
CREATE INDEX idx_instrument_liquidity_adv_updated_at ON instrument_liquidity (adv_updated_at);
