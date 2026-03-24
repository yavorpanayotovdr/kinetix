package com.kinetix.position.fix

interface ExecutionCostRepository {
    suspend fun save(analysis: ExecutionCostAnalysis)
    suspend fun findByOrderId(orderId: String): ExecutionCostAnalysis?
    suspend fun findByBookId(bookId: String): List<ExecutionCostAnalysis>
}
