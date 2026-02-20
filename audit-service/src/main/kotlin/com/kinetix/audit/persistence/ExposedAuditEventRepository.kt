package com.kinetix.audit.persistence

import com.kinetix.audit.model.AuditEvent
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExposedAuditEventRepository : AuditEventRepository {

    override suspend fun save(event: AuditEvent): Unit = newSuspendedTransaction {
        AuditEventsTable.insert {
            it[tradeId] = event.tradeId
            it[portfolioId] = event.portfolioId
            it[instrumentId] = event.instrumentId
            it[assetClass] = event.assetClass
            it[side] = event.side
            it[quantity] = event.quantity
            it[priceAmount] = event.priceAmount
            it[priceCurrency] = event.priceCurrency
            it[tradedAt] = event.tradedAt
            it[receivedAt] = OffsetDateTime.ofInstant(event.receivedAt, ZoneOffset.UTC)
        }
    }

    override suspend fun findAll(): List<AuditEvent> = newSuspendedTransaction {
        AuditEventsTable
            .selectAll()
            .orderBy(AuditEventsTable.id)
            .map { it.toAuditEvent() }
    }

    override suspend fun findByPortfolioId(portfolioId: String): List<AuditEvent> = newSuspendedTransaction {
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
        quantity = this[AuditEventsTable.quantity],
        priceAmount = this[AuditEventsTable.priceAmount],
        priceCurrency = this[AuditEventsTable.priceCurrency],
        tradedAt = this[AuditEventsTable.tradedAt],
        receivedAt = this[AuditEventsTable.receivedAt].toInstant(),
    )
}
