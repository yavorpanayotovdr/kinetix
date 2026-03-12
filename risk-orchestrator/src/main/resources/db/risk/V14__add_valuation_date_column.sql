-- Valuation date: UTC business date for which positions and market data are current.
-- Multiple calculation runs may share the same valuation date.
ALTER TABLE valuation_jobs ADD COLUMN valuation_date DATE DEFAULT NULL;
