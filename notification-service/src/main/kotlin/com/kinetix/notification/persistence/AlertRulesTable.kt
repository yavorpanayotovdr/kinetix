package com.kinetix.notification.persistence

import org.jetbrains.exposed.sql.Table

object AlertRulesTable : Table("alert_rules") {
    val id = varchar("id", 255)
    val name = varchar("name", 255)
    val type = varchar("type", 50)
    val threshold = double("threshold")
    val operator = varchar("operator", 50)
    val severity = varchar("severity", 50)
    val channels = varchar("channels", 500)
    val enabled = bool("enabled")

    override val primaryKey = PrimaryKey(id)
}
