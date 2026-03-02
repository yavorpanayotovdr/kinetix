ALTER TABLE valuation_jobs ADD COLUMN position_risk JSONB;
ALTER TABLE valuation_jobs ADD COLUMN component_breakdown JSONB;
ALTER TABLE valuation_jobs ADD COLUMN computed_outputs JSONB;
ALTER TABLE valuation_jobs ADD COLUMN asset_class_greeks JSONB;
