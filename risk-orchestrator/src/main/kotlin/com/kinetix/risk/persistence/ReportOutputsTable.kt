package com.kinetix.risk.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

object ReportOutputsTable : Table("report_outputs") {
    val outputId = varchar("output_id", 36)
    val templateId = varchar("template_id", 36)
    val generatedAt = timestampWithTimeZone("generated_at")
    val outputFormat = varchar("output_format", 20)
    val rowCount = integer("row_count")
    val outputData = jsonb<JsonArray>("output_data", Json).nullable()

    override val primaryKey = PrimaryKey(outputId)
}
