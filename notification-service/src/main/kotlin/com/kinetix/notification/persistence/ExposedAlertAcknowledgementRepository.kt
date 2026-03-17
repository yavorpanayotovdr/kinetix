package com.kinetix.notification.persistence

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExposedAlertAcknowledgementRepository(private val db: Database? = null) : AlertAcknowledgementRepository {

    override suspend fun save(ack: AlertAcknowledgement): Unit = newSuspendedTransaction(db = db) {
        AlertAcknowledgementsTable.insert {
            it[id] = ack.id
            it[alertEventId] = ack.alertEventId
            it[alertTriggeredAt] = OffsetDateTime.ofInstant(ack.alertTriggeredAt, ZoneOffset.UTC)
            it[acknowledgedBy] = ack.acknowledgedBy
            it[acknowledgedAt] = OffsetDateTime.ofInstant(ack.acknowledgedAt, ZoneOffset.UTC)
            it[notes] = ack.notes
        }
    }

    override suspend fun findByAlertId(alertEventId: String): AlertAcknowledgement? =
        newSuspendedTransaction(db = db) {
            AlertAcknowledgementsTable
                .selectAll()
                .where { AlertAcknowledgementsTable.alertEventId eq alertEventId }
                .map {
                    AlertAcknowledgement(
                        id = it[AlertAcknowledgementsTable.id],
                        alertEventId = it[AlertAcknowledgementsTable.alertEventId],
                        alertTriggeredAt = it[AlertAcknowledgementsTable.alertTriggeredAt].toInstant(),
                        acknowledgedBy = it[AlertAcknowledgementsTable.acknowledgedBy],
                        acknowledgedAt = it[AlertAcknowledgementsTable.acknowledgedAt].toInstant(),
                        notes = it[AlertAcknowledgementsTable.notes],
                    )
                }
                .firstOrNull()
        }
}
