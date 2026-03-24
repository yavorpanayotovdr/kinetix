package com.kinetix.position.fix

interface ExecutionFillRepository {
    suspend fun save(fill: ExecutionFill)
    suspend fun findByOrderId(orderId: String): List<ExecutionFill>
    suspend fun existsByFixExecId(fixExecId: String): Boolean
}
