-- Expression index matching the DISTINCT ON / ORDER BY in findDailyCloseByInstrumentId.
-- Allows Postgres to satisfy the query via an index scan instead of
-- scanning all rows for an instrument and sorting them.
--
-- Sort directions match the query exactly:
--   ORDER BY instrument_id, date(timestamp AT TIME ZONE 'UTC') ASC, timestamp DESC
CREATE INDEX idx_prices_instrument_daily
    ON prices (instrument_id, date(timestamp AT TIME ZONE 'UTC'), timestamp DESC);
