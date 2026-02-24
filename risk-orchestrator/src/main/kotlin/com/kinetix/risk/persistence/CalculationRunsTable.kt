package com.kinetix.risk.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone
import org.jetbrains.exposed.sql.json.jsonb
import kotlinx.serialization.json.Json

object CalculationRunsTable : Table("calculation_runs") {
    val runId = uuid("run_id")
    val portfolioId = varchar("portfolio_id", 255)
    val triggerType = varchar("trigger_type", 50)
    val status = varchar("status", 20)
    val startedAt = timestampWithTimeZone("started_at")
    val completedAt = timestampWithTimeZone("completed_at").nullable()
    val durationMs = long("duration_ms").nullable()
    val calculationType = varchar("calculation_type", 50).nullable()
    val confidenceLevel = varchar("confidence_level", 10).nullable()
    val varValue = double("var_value").nullable()
    val expectedShortfall = double("expected_shortfall").nullable()
    val steps = jsonb<List<PipelineStepJson>>("steps", Json)
    val error = text("error").nullable()

    override val primaryKey = PrimaryKey(runId)
}
