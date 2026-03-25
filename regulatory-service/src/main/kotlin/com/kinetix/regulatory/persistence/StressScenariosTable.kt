package com.kinetix.regulatory.persistence

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object StressScenariosTable : Table("stress_scenarios") {
    val id = varchar("id", 255)
    val name = varchar("name", 255)
    val description = text("description")
    val shocks = jsonb<JsonElement>("shocks", Json)
    val status = varchar("status", 30)
    val createdBy = varchar("created_by", 255)
    val approvedBy = varchar("approved_by", 255).nullable()
    val approvedAt = timestampWithTimeZone("approved_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val scenarioType = varchar("scenario_type", 30).default("PARAMETRIC")
    val category = varchar("category", 30).default("INTERNAL_APPROVED")
    val version = integer("version").default(1)
    val parentScenarioId = varchar("parent_scenario_id", 255).nullable()
    val correlationOverride = text("correlation_override").nullable()
    val liquidityStressFactors = text("liquidity_stress_factors").nullable()
    val historicalPeriodId = varchar("historical_period_id", 255).nullable()
    val targetLoss = decimal("target_loss", 28, 8).nullable()

    override val primaryKey = PrimaryKey(id)
}
