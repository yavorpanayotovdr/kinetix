package com.kinetix.position.persistence

import com.kinetix.position.model.CollateralBalance

interface CollateralBalanceRepository {
    suspend fun save(balance: CollateralBalance): CollateralBalance
    suspend fun findByCounterpartyId(counterpartyId: String): List<CollateralBalance>
    suspend fun findByNettingSetId(nettingSetId: String): List<CollateralBalance>
    suspend fun deleteById(id: Long)
}
