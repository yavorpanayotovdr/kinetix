-- Report template system: templates define parameterised queries;
-- users select template + parameters, never raw SQL.

CREATE TABLE IF NOT EXISTS report_templates (
    template_id   VARCHAR(36)  PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    template_type VARCHAR(50)  NOT NULL,   -- RISK_SUMMARY | STRESS_TEST_SUMMARY | PNL_ATTRIBUTION
    owner_user_id VARCHAR(255) NOT NULL,
    definition    JSONB        NOT NULL,   -- columns, filters, grouping, sort
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS report_outputs (
    output_id     VARCHAR(36)  PRIMARY KEY,
    template_id   VARCHAR(36)  NOT NULL REFERENCES report_templates(template_id),
    generated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    output_format VARCHAR(20)  NOT NULL,   -- JSON | CSV
    row_count     INTEGER      NOT NULL,
    output_data   JSONB
);

CREATE INDEX IF NOT EXISTS idx_report_outputs_template
    ON report_outputs (template_id, generated_at DESC);

-- Seed 3 built-in templates.
-- These are SYSTEM-owned and immutable at the DB layer.

INSERT INTO report_templates (template_id, name, template_type, owner_user_id, definition)
VALUES
(
    'tpl-risk-summary',
    'Risk Summary',
    'RISK_SUMMARY',
    'SYSTEM',
    '{
        "description": "Per-book VaR, ES, top 5 positions by VaR contribution, aggregate Greeks",
        "source": "risk_positions_flat",
        "columns": ["book_id","instrument_id","asset_class","quantity","market_price","delta","gamma","vega","theta","rho","var_contribution","es_contribution"],
        "orderBy": [{"column":"var_contribution","direction":"DESC"}],
        "topN": 100
    }'::jsonb
),
(
    'tpl-stress-summary',
    'Stress Test Summary',
    'STRESS_TEST_SUMMARY',
    'SYSTEM',
    '{
        "description": "All scenarios ranked by worst P&L impact for a book",
        "source": "stress_test_results",
        "columns": ["book_id","scenario_name","base_var","stressed_var","pnl_impact","calculated_at"],
        "orderBy": [{"column":"pnl_impact","direction":"ASC"}]
    }'::jsonb
),
(
    'tpl-pnl-attribution',
    'P&L Attribution',
    'PNL_ATTRIBUTION',
    'SYSTEM',
    '{
        "description": "Daily P&L decomposition by Greek factor",
        "source": "pnl_attributions",
        "columns": ["book_id","attribution_date","total_pnl","delta_pnl","gamma_pnl","vega_pnl","theta_pnl","rho_pnl","unexplained_pnl"],
        "orderBy": [{"column":"attribution_date","direction":"DESC"}]
    }'::jsonb
)
ON CONFLICT (template_id) DO NOTHING;
