package com.kinetix.notification.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object AlertEventsTable : Table("alert_events") {
    val id = varchar("id", 255)
    val ruleId = varchar("rule_id", 255)
    val ruleName = varchar("rule_name", 255)
    val type = varchar("type", 50)
    val severity = varchar("severity", 50)
    val message = text("message")
    val currentValue = double("current_value")
    val threshold = double("threshold")
    val portfolioId = varchar("portfolio_id", 255)
    val triggeredAt = timestampWithTimeZone("triggered_at")

    override val primaryKey = PrimaryKey(id)
}
