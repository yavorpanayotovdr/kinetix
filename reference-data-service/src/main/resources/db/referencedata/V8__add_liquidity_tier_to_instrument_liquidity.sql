-- Adds the liquidity_tier column to instrument_liquidity.
-- Tier is classified at ingestion time using ADV and bid-ask spread thresholds:
--   TIER_1: adv >= 50M and spread <= 5bps
--   TIER_2: adv >= 10M and spread <= 20bps
--   TIER_3: adv >= 1M
--   ILLIQUID: everything else, or no ADV data
ALTER TABLE instrument_liquidity
    ADD COLUMN IF NOT EXISTS liquidity_tier VARCHAR(20) NOT NULL DEFAULT 'ILLIQUID';
