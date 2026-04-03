package com.kinetix.audit.persistence

import com.kinetix.audit.model.AuditEvent
import java.time.Instant

interface AuditEventRepository {
    suspend fun save(event: AuditEvent)
    suspend fun findAll(): List<AuditEvent>
    suspend fun findByBookId(bookId: String): List<AuditEvent>
    suspend fun findPage(afterId: Long, limit: Int): List<AuditEvent>
    suspend fun countAll(): Long
    suspend fun countSince(since: Instant): Long
    suspend fun findByTradeId(tradeId: String): AuditEvent?
    suspend fun nextSequenceNumber(): Long
}
