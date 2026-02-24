ALTER TABLE market_data RENAME TO prices;
ALTER INDEX idx_market_data_instrument RENAME TO idx_prices_instrument;
