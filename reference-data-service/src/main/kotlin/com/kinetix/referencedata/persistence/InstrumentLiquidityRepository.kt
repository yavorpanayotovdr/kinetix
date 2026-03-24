package com.kinetix.referencedata.persistence

import com.kinetix.referencedata.model.InstrumentLiquidity

interface InstrumentLiquidityRepository {
    suspend fun findById(instrumentId: String): InstrumentLiquidity?
    suspend fun findByIds(instrumentIds: List<String>): List<InstrumentLiquidity>
    suspend fun upsert(liquidity: InstrumentLiquidity)
    suspend fun findAll(): List<InstrumentLiquidity>
}
