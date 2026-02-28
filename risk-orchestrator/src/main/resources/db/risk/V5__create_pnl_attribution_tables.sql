CREATE TABLE daily_risk_snapshots (
    id BIGSERIAL PRIMARY KEY,
    portfolio_id VARCHAR(64) NOT NULL,
    snapshot_date DATE NOT NULL,
    instrument_id VARCHAR(64) NOT NULL,
    asset_class VARCHAR(32) NOT NULL,
    quantity DECIMAL(20,8) NOT NULL,
    market_price DECIMAL(20,8) NOT NULL,
    delta DOUBLE PRECISION,
    gamma DOUBLE PRECISION,
    vega DOUBLE PRECISION,
    theta DOUBLE PRECISION,
    rho DOUBLE PRECISION,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(portfolio_id, snapshot_date, instrument_id)
);

CREATE TABLE pnl_attributions (
    id BIGSERIAL PRIMARY KEY,
    portfolio_id VARCHAR(64) NOT NULL,
    attribution_date DATE NOT NULL,
    total_pnl DECIMAL(20,8) NOT NULL,
    delta_pnl DECIMAL(20,8) NOT NULL,
    gamma_pnl DECIMAL(20,8) NOT NULL,
    vega_pnl DECIMAL(20,8) NOT NULL,
    theta_pnl DECIMAL(20,8) NOT NULL,
    rho_pnl DECIMAL(20,8) NOT NULL,
    unexplained_pnl DECIMAL(20,8) NOT NULL,
    position_attributions JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(portfolio_id, attribution_date)
);
