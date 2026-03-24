package com.kinetix.position.fix

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExposedFIXSessionRepository(private val db: Database? = null) : FIXSessionRepository {

    override suspend fun save(session: FIXSession): Unit = newSuspendedTransaction(db = db) {
        FIXSessionsTable.insert {
            it[sessionId] = session.sessionId
            it[counterparty] = session.counterparty
            it[status] = session.status.name
            it[lastMessageAt] = session.lastMessageAt?.atOffset(ZoneOffset.UTC)
            it[inboundSeqNum] = session.inboundSeqNum
            it[outboundSeqNum] = session.outboundSeqNum
            it[updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
    }

    override suspend fun findById(sessionId: String): FIXSession? = newSuspendedTransaction(db = db) {
        FIXSessionsTable
            .selectAll()
            .where { FIXSessionsTable.sessionId eq sessionId }
            .singleOrNull()
            ?.toSession()
    }

    override suspend fun findAll(): List<FIXSession> = newSuspendedTransaction(db = db) {
        FIXSessionsTable.selectAll().map { it.toSession() }
    }

    override suspend fun updateStatus(sessionId: String, status: FIXSessionStatus): Unit =
        newSuspendedTransaction(db = db) {
            FIXSessionsTable.update({ FIXSessionsTable.sessionId eq sessionId }) {
                it[FIXSessionsTable.status] = status.name
                it[updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
            }
        }

    private fun ResultRow.toSession() = FIXSession(
        sessionId = this[FIXSessionsTable.sessionId],
        counterparty = this[FIXSessionsTable.counterparty],
        status = FIXSessionStatus.valueOf(this[FIXSessionsTable.status]),
        lastMessageAt = this[FIXSessionsTable.lastMessageAt]?.toInstant(),
        inboundSeqNum = this[FIXSessionsTable.inboundSeqNum],
        outboundSeqNum = this[FIXSessionsTable.outboundSeqNum],
    )
}
