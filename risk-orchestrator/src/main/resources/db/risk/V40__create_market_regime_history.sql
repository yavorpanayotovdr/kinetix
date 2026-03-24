-- V40: Market regime history hypertable
-- Stores the history of confirmed market regime transitions.
-- Hypertable partitioned by started_at for efficient time-range queries.
-- Retention: 5 years (1825 days) per spec.

CREATE TABLE market_regime_history (
    id           UUID        NOT NULL DEFAULT gen_random_uuid(),
    regime       TEXT        NOT NULL,
    started_at   TIMESTAMPTZ NOT NULL,
    ended_at     TIMESTAMPTZ,
    duration_ms  BIGINT,

    -- Signals at transition
    realised_vol_20d       NUMERIC(12, 6) NOT NULL,
    cross_asset_correlation NUMERIC(12, 6) NOT NULL,
    credit_spread_bps      NUMERIC(12, 4),
    pnl_volatility         NUMERIC(12, 6),

    -- VaR parameters in effect during this regime
    calculation_type   TEXT    NOT NULL,
    confidence_level   TEXT    NOT NULL,
    time_horizon_days  INTEGER NOT NULL,
    correlation_method TEXT    NOT NULL,
    num_simulations    INTEGER,

    -- Classification metadata
    confidence              NUMERIC(6, 4) NOT NULL,
    degraded_inputs         BOOLEAN NOT NULL DEFAULT FALSE,
    consecutive_observations INTEGER NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT market_regime_history_pkey PRIMARY KEY (id, started_at)
);

-- Convert to TimescaleDB hypertable partitioned by started_at
SELECT create_hypertable(
    'market_regime_history',
    'started_at',
    chunk_time_interval => INTERVAL '1 month',
    if_not_exists => TRUE
);

-- Retention policy: keep 5 years (1825 days)
SELECT add_retention_policy(
    'market_regime_history',
    INTERVAL '1825 days',
    if_not_exists => TRUE
);

-- Compression after 30 days
ALTER TABLE market_regime_history SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'regime',
    timescaledb.compress_orderby = 'started_at DESC'
);

SELECT add_compression_policy(
    'market_regime_history',
    INTERVAL '30 days',
    if_not_exists => TRUE
);

-- Index for current-regime lookups
CREATE INDEX market_regime_history_ended_at_null_idx
    ON market_regime_history (ended_at)
    WHERE ended_at IS NULL;

-- Index for regime + time range queries (analytics)
CREATE INDEX market_regime_history_regime_started_at_idx
    ON market_regime_history (regime, started_at DESC);
