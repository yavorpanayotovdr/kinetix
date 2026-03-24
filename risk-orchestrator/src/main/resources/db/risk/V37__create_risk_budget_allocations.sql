-- V37: Create risk_budget_allocations table.
-- Stores soft risk budgets per hierarchy entity.
-- Budgets are strategic soft targets — exceeding one fires alerts
-- and triggers risk committee review. They do NOT block trades.
-- budget_type: VAR, NOTIONAL, ES
-- budget_period: daily, monthly, quarterly

CREATE TABLE risk_budget_allocations (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    entity_level     VARCHAR(16)  NOT NULL,
    entity_id        VARCHAR(64)  NOT NULL,
    budget_type      VARCHAR(16)  NOT NULL,
    budget_period    VARCHAR(16)  NOT NULL,
    budget_amount    NUMERIC(24, 6) NOT NULL,
    effective_from   DATE         NOT NULL,
    effective_to     DATE,
    allocated_by     VARCHAR(255) NOT NULL,
    allocation_note  TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_risk_budget_allocations PRIMARY KEY (id)
);

-- Primary query: find effective budget for an entity at a given date.
CREATE INDEX idx_risk_budget_entity_type
    ON risk_budget_allocations (entity_level, entity_id, budget_type, effective_from DESC);
