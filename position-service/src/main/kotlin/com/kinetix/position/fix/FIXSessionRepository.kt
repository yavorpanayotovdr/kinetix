package com.kinetix.position.fix

interface FIXSessionRepository {
    suspend fun save(session: FIXSession)
    suspend fun findById(sessionId: String): FIXSession?
    suspend fun findAll(): List<FIXSession>
    suspend fun updateStatus(sessionId: String, status: FIXSessionStatus)
}
