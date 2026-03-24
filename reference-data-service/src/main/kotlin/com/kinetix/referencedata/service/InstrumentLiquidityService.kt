package com.kinetix.referencedata.service

import com.kinetix.referencedata.model.InstrumentLiquidity
import com.kinetix.referencedata.persistence.InstrumentLiquidityRepository
import java.time.Instant

private const val ADV_MAX_STALENESS_DAYS = 2L

class InstrumentLiquidityService(
    private val repository: InstrumentLiquidityRepository,
) {

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
