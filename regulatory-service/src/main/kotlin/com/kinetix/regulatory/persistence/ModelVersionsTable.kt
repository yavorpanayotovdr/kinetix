package com.kinetix.regulatory.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object ModelVersionsTable : Table("model_versions") {
    val id = varchar("id", 255)
    val modelName = varchar("model_name", 255)
    val version = varchar("version", 50)
    val status = varchar("status", 20)
    val parameters = text("parameters")
    val approvedBy = varchar("approved_by", 255).nullable()
    val approvedAt = timestampWithTimeZone("approved_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}
