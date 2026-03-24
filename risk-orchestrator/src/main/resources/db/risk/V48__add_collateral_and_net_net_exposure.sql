ALTER TABLE counterparty_exposure_history
    ADD COLUMN IF NOT EXISTS collateral_held  NUMERIC(24, 6) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS collateral_posted NUMERIC(24, 6) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS net_net_exposure  NUMERIC(24, 6);
