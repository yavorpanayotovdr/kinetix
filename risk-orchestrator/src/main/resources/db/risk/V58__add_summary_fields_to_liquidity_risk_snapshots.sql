ALTER TABLE liquidity_risk_snapshots ADD COLUMN IF NOT EXISTS var_1day NUMERIC(24,6) NOT NULL DEFAULT 0;
ALTER TABLE liquidity_risk_snapshots ADD COLUMN IF NOT EXISTS lvar_ratio NUMERIC(10,6) NOT NULL DEFAULT 0;
ALTER TABLE liquidity_risk_snapshots ADD COLUMN IF NOT EXISTS weighted_avg_horizon NUMERIC(10,4) NOT NULL DEFAULT 0;
ALTER TABLE liquidity_risk_snapshots ADD COLUMN IF NOT EXISTS max_horizon NUMERIC(10,4) NOT NULL DEFAULT 0;
ALTER TABLE liquidity_risk_snapshots ADD COLUMN IF NOT EXISTS concentration_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE liquidity_risk_snapshots ADD COLUMN IF NOT EXISTS adv_data_as_of TIMESTAMPTZ;
