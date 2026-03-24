package com.kinetix.referencedata.service

import com.kinetix.referencedata.model.InstrumentLiquidity
import com.kinetix.referencedata.model.InstrumentLiquidityTier
import com.kinetix.referencedata.persistence.InstrumentLiquidityRepository
import java.time.Instant

private const val ADV_MAX_STALENESS_DAYS = 2L

private const val TIER_1_ADV_FLOOR = 50_000_000.0
private const val TIER_1_SPREAD_CAP = 5.0
private const val TIER_2_ADV_FLOOR = 10_000_000.0
private const val TIER_2_SPREAD_CAP = 20.0
private const val TIER_3_ADV_FLOOR = 1_000_000.0

class InstrumentLiquidityService(
    private val repository: InstrumentLiquidityRepository,
) {

    companion object {
        /**
         * Classifies an instrument into a liquidity tier based on ADV and bid-ask spread.
         *
         * TIER_1: adv >= 50M and spread <= 5bps
         * TIER_2: adv >= 10M and spread <= 20bps
         * TIER_3: adv >= 1M
         * ILLIQUID: everything else
         */
        fun classifyTier(adv: Double, bidAskSpreadBps: Double): InstrumentLiquidityTier = when {
            adv >= TIER_1_ADV_FLOOR && bidAskSpreadBps <= TIER_1_SPREAD_CAP -> InstrumentLiquidityTier.TIER_1
            adv >= TIER_2_ADV_FLOOR && bidAskSpreadBps <= TIER_2_SPREAD_CAP -> InstrumentLiquidityTier.TIER_2
            adv >= TIER_3_ADV_FLOOR -> InstrumentLiquidityTier.TIER_3
            else -> InstrumentLiquidityTier.ILLIQUID
        }
    }

    suspend fun findById(instrumentId: String): InstrumentLiquidity? =
        repository.findById(instrumentId)

    suspend fun findByIds(instrumentIds: List<String>): List<InstrumentLiquidity> =
        repository.findByIds(instrumentIds)

    suspend fun upsert(liquidity: InstrumentLiquidity) {
        repository.upsert(liquidity)
    }

    suspend fun findAll(): List<InstrumentLiquidity> = repository.findAll()

    fun isStale(liquidity: InstrumentLiquidity, now: Instant = Instant.now()): Boolean {
        val ageSeconds = now.epochSecond - liquidity.advUpdatedAt.epochSecond
        return ageSeconds > ADV_MAX_STALENESS_DAYS * 24 * 3600
    }

    fun staleDays(liquidity: InstrumentLiquidity, now: Instant = Instant.now()): Int {
        val ageSeconds = now.epochSecond - liquidity.advUpdatedAt.epochSecond
        return (ageSeconds / (24 * 3600)).toInt()
    }
}
