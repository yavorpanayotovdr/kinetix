package com.kinetix.notification.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object AlertAcknowledgementsTable : Table("alert_acknowledgements") {
    val id = varchar("id", 255)
    val alertEventId = varchar("alert_event_id", 255)
    val alertTriggeredAt = timestampWithTimeZone("alert_triggered_at")
    val acknowledgedBy = varchar("acknowledged_by", 255)
    val acknowledgedAt = timestampWithTimeZone("acknowledged_at")
    val notes = text("notes").nullable()

    override val primaryKey = PrimaryKey(id)
}
