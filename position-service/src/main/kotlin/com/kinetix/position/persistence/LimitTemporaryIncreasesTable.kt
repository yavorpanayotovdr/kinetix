package com.kinetix.position.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object LimitTemporaryIncreasesTable : Table("limit_temporary_increases") {
    val id = varchar("id", 255)
    val limitId = varchar("limit_id", 255).references(LimitDefinitionsTable.id)
    val newValue = decimal("new_value", 28, 12)
    val approvedBy = varchar("approved_by", 255)
    val expiresAt = timestampWithTimeZone("expires_at")
    val reason = text("reason")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}
