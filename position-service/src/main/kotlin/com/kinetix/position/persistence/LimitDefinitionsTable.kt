package com.kinetix.position.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object LimitDefinitionsTable : Table("limit_definitions") {
    val id = varchar("id", 255)
    val level = varchar("level", 20)
    val entityId = varchar("entity_id", 255)
    val limitType = varchar("limit_type", 20)
    val limitValue = decimal("limit_value", 28, 12)
    val intradayLimit = decimal("intraday_limit", 28, 12).nullable()
    val overnightLimit = decimal("overnight_limit", 28, 12).nullable()
    val active = bool("active")
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}
