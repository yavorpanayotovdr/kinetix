package com.kinetix.position.fix

interface ExecutionOrderRepository {
    suspend fun save(order: Order)
    suspend fun updateStatus(orderId: String, status: OrderStatus, riskCheckResult: String? = null, riskCheckDetails: String? = null)
    suspend fun findById(orderId: String): Order?
    suspend fun findByBookId(bookId: String): List<Order>
}
