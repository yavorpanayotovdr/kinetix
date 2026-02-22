package com.kinetix.notification.persistence

import com.kinetix.notification.model.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExposedAlertEventRepository(private val db: Database? = null) : AlertEventRepository {

    override suspend fun save(event: AlertEvent): Unit = newSuspendedTransaction(db = db) {
        AlertEventsTable.insert {
            it[id] = event.id
            it[ruleId] = event.ruleId
            it[ruleName] = event.ruleName
            it[type] = event.type.name
            it[severity] = event.severity.name
            it[message] = event.message
            it[currentValue] = event.currentValue
            it[threshold] = event.threshold
            it[portfolioId] = event.portfolioId
            it[triggeredAt] = OffsetDateTime.ofInstant(event.triggeredAt, ZoneOffset.UTC)
        }
    }

    override suspend fun findRecent(limit: Int): List<AlertEvent> = newSuspendedTransaction(db = db) {
        AlertEventsTable
            .selectAll()
            .orderBy(AlertEventsTable.triggeredAt, SortOrder.DESC)
            .limit(limit)
            .map { it.toAlertEvent() }
    }

    private fun ResultRow.toAlertEvent(): AlertEvent = AlertEvent(
        id = this[AlertEventsTable.id],
        ruleId = this[AlertEventsTable.ruleId],
        ruleName = this[AlertEventsTable.ruleName],
        type = AlertType.valueOf(this[AlertEventsTable.type]),
        severity = Severity.valueOf(this[AlertEventsTable.severity]),
        message = this[AlertEventsTable.message],
        currentValue = this[AlertEventsTable.currentValue],
        threshold = this[AlertEventsTable.threshold],
        portfolioId = this[AlertEventsTable.portfolioId],
        triggeredAt = this[AlertEventsTable.triggeredAt].toInstant(),
    )
}
