package com.kinetix.audit.persistence

import com.kinetix.audit.model.AuditEvent
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExposedAuditEventRepository(private val db: Database? = null) : AuditEventRepository {

    override suspend fun save(event: AuditEvent): Unit = newSuspendedTransaction(db = db) {
        val latestHash = AuditEventsTable
            .select(AuditEventsTable.recordHash)
            .orderBy(AuditEventsTable.id, SortOrder.DESC)
            .limit(1)
            .map { it[AuditEventsTable.recordHash] }
            .firstOrNull()
            ?.takeIf { it.isNotEmpty() }

        val recordHash = AuditHasher.computeHash(event, latestHash)

        AuditEventsTable.insert {
            it[tradeId] = event.tradeId
            it[portfolioId] = event.portfolioId
            it[instrumentId] = event.instrumentId
            it[assetClass] = event.assetClass
            it[side] = event.side
            it[quantity] = event.quantity.toBigDecimal()
            it[priceAmount] = event.priceAmount.toBigDecimal()
            it[priceCurrency] = event.priceCurrency
            it[tradedAt] = OffsetDateTime.ofInstant(Instant.parse(event.tradedAt), ZoneOffset.UTC)
            it[receivedAt] = OffsetDateTime.ofInstant(event.receivedAt, ZoneOffset.UTC)
            it[AuditEventsTable.previousHash] = latestHash
            it[AuditEventsTable.recordHash] = recordHash
            it[userId] = event.userId
            it[userRole] = event.userRole
            it[eventType] = event.eventType
        }
    }

    override suspend fun findAll(): List<AuditEvent> = newSuspendedTransaction(db = db) {
        AuditEventsTable
            .selectAll()
            .orderBy(AuditEventsTable.id)
            .map { it.toAuditEvent() }
    }

    override suspend fun findByPortfolioId(portfolioId: String): List<AuditEvent> = newSuspendedTransaction(db = db) {
        AuditEventsTable
            .selectAll()
            .where { AuditEventsTable.portfolioId eq portfolioId }
            .orderBy(AuditEventsTable.id)
            .map { it.toAuditEvent() }
    }

    private fun ResultRow.toAuditEvent(): AuditEvent = AuditEvent(
        id = this[AuditEventsTable.id],
        tradeId = this[AuditEventsTable.tradeId],
        portfolioId = this[AuditEventsTable.portfolioId],
        instrumentId = this[AuditEventsTable.instrumentId],
        assetClass = this[AuditEventsTable.assetClass],
        side = this[AuditEventsTable.side],
        quantity = this[AuditEventsTable.quantity].stripTrailingZeros().toPlainString(),
        priceAmount = this[AuditEventsTable.priceAmount].setScale(2).toPlainString(),
        priceCurrency = this[AuditEventsTable.priceCurrency],
        tradedAt = this[AuditEventsTable.tradedAt].toInstant().toString(),
        receivedAt = this[AuditEventsTable.receivedAt].toInstant(),
        previousHash = this[AuditEventsTable.previousHash],
        recordHash = this[AuditEventsTable.recordHash],
        userId = this[AuditEventsTable.userId],
        userRole = this[AuditEventsTable.userRole],
        eventType = this[AuditEventsTable.eventType],
    )
}
