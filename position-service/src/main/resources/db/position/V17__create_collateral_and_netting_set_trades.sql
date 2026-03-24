-- Collateral balances: tracks collateral posted to or received from each counterparty.
-- For v1: updated manually via API.
-- direction: POSTED (we gave to counterparty) | RECEIVED (counterparty gave to us)
-- collateral_type: CASH | GOVERNMENT_BOND | CORPORATE_BOND | EQUITY
-- value_after_haircut: amount * (1 - haircut)
--   Haircuts: CASH 0%, GOVERNMENT_BOND 3%, CORPORATE_BOND 10%, EQUITY 20%

CREATE TABLE collateral_balances (
    id                  BIGSERIAL      NOT NULL,
    counterparty_id     VARCHAR(255)   NOT NULL,
    netting_set_id      VARCHAR(255),
    collateral_type     VARCHAR(30)    NOT NULL,
    amount              NUMERIC(24,6)  NOT NULL,
    currency            VARCHAR(3)     NOT NULL DEFAULT 'USD',
    direction           VARCHAR(10)    NOT NULL,
    as_of_date          DATE           NOT NULL,
    value_after_haircut NUMERIC(24,6)  NOT NULL,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_collateral_balances PRIMARY KEY (id),
    CONSTRAINT chk_collateral_direction CHECK (direction IN ('POSTED', 'RECEIVED')),
    CONSTRAINT chk_collateral_type CHECK (collateral_type IN ('CASH', 'GOVERNMENT_BOND', 'CORPORATE_BOND', 'EQUITY'))
);

CREATE INDEX idx_collateral_balances_counterparty ON collateral_balances (counterparty_id);
CREATE INDEX idx_collateral_balances_netting_set  ON collateral_balances (netting_set_id) WHERE netting_set_id IS NOT NULL;
CREATE INDEX idx_collateral_balances_as_of_date   ON collateral_balances (as_of_date DESC);

-- Links trades to netting agreements.
-- A trade belongs to at most one netting set.
CREATE TABLE netting_set_trades (
    trade_id        VARCHAR(255) NOT NULL,
    netting_set_id  VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_netting_set_trades PRIMARY KEY (trade_id)
);

CREATE INDEX idx_netting_set_trades_netting_set ON netting_set_trades (netting_set_id);
