ALTER TABLE daily_risk_snapshots
    ADD COLUMN IF NOT EXISTS var_contribution NUMERIC(28, 8),
    ADD COLUMN IF NOT EXISTS es_contribution  NUMERIC(28, 8);
