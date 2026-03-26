package com.kinetix.position.fix

interface PrimeBrokerReconciliationRepository {
    suspend fun save(reconciliation: PrimeBrokerReconciliation, id: String)
    suspend fun findByBookId(bookId: String): List<PrimeBrokerReconciliation>
    suspend fun findLatestByBookId(bookId: String): PrimeBrokerReconciliation?
    suspend fun findById(id: String): PrimeBrokerReconciliation?
    suspend fun updateBreakStatus(reconciliationId: String, instrumentId: String, status: ReconciliationBreakStatus)
}
