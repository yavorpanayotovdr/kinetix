CREATE MATERIALIZED VIEW daily_close_prices
WITH (timescaledb.continuous) AS
SELECT
    instrument_id,
    time_bucket('1 day', timestamp) AS bucket,
    last(price_amount, timestamp)   AS close_price_amount,
    last(price_currency, timestamp) AS close_price_currency,
    last(source, timestamp)         AS close_source,
    last(timestamp, timestamp)      AS close_timestamp
FROM prices
GROUP BY instrument_id, time_bucket('1 day', timestamp)
WITH NO DATA;

SELECT add_continuous_aggregate_policy('daily_close_prices',
    start_offset    => INTERVAL '30 days',
    end_offset      => INTERVAL '2 hours',
    schedule_interval => INTERVAL '1 hour'
);
