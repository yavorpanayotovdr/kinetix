package com.kinetix.risk.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone
import kotlinx.serialization.json.Json

object ReportTemplatesTable : Table("report_templates") {
    val templateId = varchar("template_id", 36)
    val name = varchar("name", 255)
    val templateType = varchar("template_type", 50)
    val ownerUserId = varchar("owner_user_id", 255)
    val definition = jsonb<ReportDefinitionJson>("definition", Json)
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(templateId)
}
