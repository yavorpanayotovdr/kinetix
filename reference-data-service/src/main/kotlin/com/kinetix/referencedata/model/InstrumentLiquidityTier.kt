package com.kinetix.referencedata.model

/**
 * Instrument liquidity tier classification based on average daily volume (ADV) and
 * bid-ask spread. Matches the spec thresholds in liquidity.allium:
 *
 *   TIER_1:  adv >= 50,000,000 AND spread <= 5 bps
 *   TIER_2:  adv >= 10,000,000 AND spread <= 20 bps
 *   TIER_3:  adv >= 1,000,000
 *   ILLIQUID: everything else, or no ADV data
 */
enum class InstrumentLiquidityTier {
    TIER_1,
    TIER_2,
    TIER_3,
    ILLIQUID,
}
