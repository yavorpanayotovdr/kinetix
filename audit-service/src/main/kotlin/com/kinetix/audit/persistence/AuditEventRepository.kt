package com.kinetix.audit.persistence

import com.kinetix.audit.model.AuditEvent

interface AuditEventRepository {
    suspend fun save(event: AuditEvent)
    suspend fun findAll(): List<AuditEvent>
    suspend fun findByPortfolioId(portfolioId: String): List<AuditEvent>
    suspend fun findPage(afterId: Long, limit: Int): List<AuditEvent>
    suspend fun countAll(): Long
}
