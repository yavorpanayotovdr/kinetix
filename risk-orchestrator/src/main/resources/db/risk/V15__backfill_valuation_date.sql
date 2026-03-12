UPDATE valuation_jobs
SET valuation_date = (started_at AT TIME ZONE 'UTC')::date
WHERE valuation_date IS NULL;
