package com.kinetix.risk.persistence

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object HedgeRecommendationsTable : Table("hedge_recommendations") {
    val id = uuid("id").clientDefault { java.util.UUID.randomUUID() }
    val bookId = varchar("book_id", 255)
    val targetMetric = varchar("target_metric", 20)
    val targetReductionPct = decimal("target_reduction_pct", precision = 8, scale = 6)
    val requestedAt = timestampWithTimeZone("requested_at")
    val status = varchar("status", 20)
    val expiresAt = timestampWithTimeZone("expires_at")
    val acceptedBy = varchar("accepted_by", 255).nullable()
    val acceptedAt = timestampWithTimeZone("accepted_at").nullable()
    val sourceJobId = varchar("source_job_id", 255).nullable()
    val constraintsJson = jsonb<JsonElement>("constraints_json", Json)
    val suggestionsJson = jsonb<JsonElement>("suggestions_json", Json)
    val preHedgeGreeksJson = jsonb<JsonElement>("pre_hedge_greeks_json", Json)

    override val primaryKey = PrimaryKey(id)
}
