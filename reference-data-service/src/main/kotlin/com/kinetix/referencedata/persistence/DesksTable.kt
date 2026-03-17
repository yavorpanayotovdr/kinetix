package com.kinetix.referencedata.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object DesksTable : Table("desks") {
    val id = varchar("id", 255)
    val name = varchar("name", 255)
    val divisionId = varchar("division_id", 255).references(DivisionsTable.id)
    val deskHead = varchar("desk_head", 255).nullable()
    val description = text("description").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}
