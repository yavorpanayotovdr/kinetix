package com.kinetix.risk.persistence

import com.kinetix.risk.model.GreekImpact
import com.kinetix.risk.model.HedgeConstraints
import com.kinetix.risk.model.HedgeRecommendation
import com.kinetix.risk.model.HedgeStatus
import com.kinetix.risk.model.HedgeSuggestion
import com.kinetix.risk.model.HedgeTarget
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.jetbrains.exposed.sql.and
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.math.BigDecimal
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class ExposedHedgeRecommendationRepository(
    private val db: Database? = null,
) : HedgeRecommendationRepository {

    override suspend fun save(recommendation: HedgeRecommendation): Unit = newSuspendedTransaction(db = db) {
        HedgeRecommendationsTable.insert {
            it[id] = recommendation.id
            it[bookId] = recommendation.bookId
            it[targetMetric] = recommendation.targetMetric.name
            it[targetReductionPct] = BigDecimal.valueOf(recommendation.targetReductionPct)
            it[requestedAt] = recommendation.requestedAt.atOffset(ZoneOffset.UTC)
            it[status] = recommendation.status.name
            it[expiresAt] = recommendation.expiresAt.atOffset(ZoneOffset.UTC)
            it[acceptedBy] = recommendation.acceptedBy
            it[acceptedAt] = recommendation.acceptedAt?.atOffset(ZoneOffset.UTC)
            it[sourceJobId] = recommendation.sourceJobId
            it[constraintsJson] = constraintsToJson(recommendation.constraints)
            it[suggestionsJson] = suggestionsToJson(recommendation.suggestions)
            it[preHedgeGreeksJson] = greekImpactToJson(recommendation.preHedgeGreeks)
        }
    }

    override suspend fun findById(id: UUID): HedgeRecommendation? = newSuspendedTransaction(db = db) {
        HedgeRecommendationsTable
            .selectAll()
            .where { HedgeRecommendationsTable.id eq id }
            .singleOrNull()
            ?.let { rowToRecommendation(it) }
    }

    override suspend fun findLatestByBookId(bookId: String, limit: Int): List<HedgeRecommendation> =
        newSuspendedTransaction(db = db) {
            HedgeRecommendationsTable
                .selectAll()
                .where { HedgeRecommendationsTable.bookId eq bookId }
                .orderBy(HedgeRecommendationsTable.requestedAt, SortOrder.DESC)
                .limit(limit)
                .map { rowToRecommendation(it) }
        }

    override suspend fun updateStatus(
        id: UUID,
        status: HedgeStatus,
        acceptedBy: String?,
        acceptedAt: Instant?,
    ): Unit = newSuspendedTransaction(db = db) {
        HedgeRecommendationsTable.update({ HedgeRecommendationsTable.id eq id }) {
            it[this.status] = status.name
            if (acceptedBy != null) it[this.acceptedBy] = acceptedBy
            if (acceptedAt != null) it[this.acceptedAt] = acceptedAt.atOffset(ZoneOffset.UTC)
        }
    }

    override suspend fun expirePending(): Int = newSuspendedTransaction(db = db) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        var count = 0
        HedgeRecommendationsTable
            .selectAll()
            .where {
                (HedgeRecommendationsTable.status eq "PENDING") and
                    (HedgeRecommendationsTable.expiresAt lessEq now)
            }
            .forEach { row ->
                HedgeRecommendationsTable.update({ HedgeRecommendationsTable.id eq row[HedgeRecommendationsTable.id] }) {
                    it[status] = HedgeStatus.EXPIRED.name
                }
                count++
            }
        count
    }

    private fun rowToRecommendation(row: org.jetbrains.exposed.sql.ResultRow): HedgeRecommendation {
        return HedgeRecommendation(
            id = row[HedgeRecommendationsTable.id],
            bookId = row[HedgeRecommendationsTable.bookId],
            targetMetric = HedgeTarget.valueOf(row[HedgeRecommendationsTable.targetMetric]),
            targetReductionPct = row[HedgeRecommendationsTable.targetReductionPct].toDouble(),
            requestedAt = row[HedgeRecommendationsTable.requestedAt].toInstant(),
            status = HedgeStatus.valueOf(row[HedgeRecommendationsTable.status]),
            expiresAt = row[HedgeRecommendationsTable.expiresAt].toInstant(),
            acceptedBy = row[HedgeRecommendationsTable.acceptedBy],
            acceptedAt = row[HedgeRecommendationsTable.acceptedAt]?.toInstant(),
            sourceJobId = row[HedgeRecommendationsTable.sourceJobId],
            constraints = constraintsFromJson(row[HedgeRecommendationsTable.constraintsJson] as? JsonObject ?: JsonObject(emptyMap())),
            suggestions = suggestionsFromJson(row[HedgeRecommendationsTable.suggestionsJson] as? JsonArray ?: JsonArray(emptyList())),
            preHedgeGreeks = greekImpactFromJson(row[HedgeRecommendationsTable.preHedgeGreeksJson] as? JsonObject ?: JsonObject(emptyMap())),
        )
    }

    private fun constraintsToJson(c: HedgeConstraints) = buildJsonObject {
        if (c.maxNotional != null) put("maxNotional", c.maxNotional)
        put("maxSuggestions", c.maxSuggestions)
        put("respectPositionLimits", c.respectPositionLimits)
        if (c.instrumentUniverse != null) put("instrumentUniverse", c.instrumentUniverse)
        if (c.allowedSides != null) {
            putJsonArray("allowedSides") { c.allowedSides.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
        }
    }

    private fun constraintsFromJson(obj: JsonObject) = HedgeConstraints(
        maxNotional = obj["maxNotional"]?.jsonPrimitive?.double,
        maxSuggestions = obj["maxSuggestions"]?.jsonPrimitive?.int ?: 5,
        respectPositionLimits = obj["respectPositionLimits"]?.jsonPrimitive?.boolean ?: true,
        instrumentUniverse = obj["instrumentUniverse"]?.jsonPrimitive?.content,
        allowedSides = obj["allowedSides"]?.jsonArray?.map { it.jsonPrimitive.content },
    )

    private fun suggestionsToJson(suggestions: List<HedgeSuggestion>) = buildJsonArray {
        suggestions.forEach { s ->
            add(buildJsonObject {
                put("instrumentId", s.instrumentId)
                put("instrumentType", s.instrumentType)
                put("side", s.side)
                put("quantity", s.quantity)
                put("estimatedCost", s.estimatedCost)
                put("crossingCost", s.crossingCost)
                if (s.carrycostPerDay != null) put("carrycostPerDay", s.carrycostPerDay)
                put("targetReduction", s.targetReduction)
                put("targetReductionPct", s.targetReductionPct)
                put("residualMetric", s.residualMetric)
                put("liquidityTier", s.liquidityTier)
                put("dataQuality", s.dataQuality)
                putJsonObject("greekImpact") {
                    put("deltaBefore", s.greekImpact.deltaBefore)
                    put("deltaAfter", s.greekImpact.deltaAfter)
                    put("gammaBefore", s.greekImpact.gammaBefore)
                    put("gammaAfter", s.greekImpact.gammaAfter)
                    put("vegaBefore", s.greekImpact.vegaBefore)
                    put("vegaAfter", s.greekImpact.vegaAfter)
                    put("thetaBefore", s.greekImpact.thetaBefore)
                    put("thetaAfter", s.greekImpact.thetaAfter)
                    put("rhoBefore", s.greekImpact.rhoBefore)
                    put("rhoAfter", s.greekImpact.rhoAfter)
                }
            })
        }
    }

    private fun suggestionsFromJson(arr: JsonArray): List<HedgeSuggestion> = arr.mapNotNull { elem ->
        val obj = elem as? JsonObject ?: return@mapNotNull null
        val greekObj = obj["greekImpact"]?.jsonObject ?: return@mapNotNull null
        HedgeSuggestion(
            instrumentId = obj["instrumentId"]?.jsonPrimitive?.content ?: return@mapNotNull null,
            instrumentType = obj["instrumentType"]?.jsonPrimitive?.content ?: "",
            side = obj["side"]?.jsonPrimitive?.content ?: "BUY",
            quantity = obj["quantity"]?.jsonPrimitive?.double ?: 0.0,
            estimatedCost = obj["estimatedCost"]?.jsonPrimitive?.double ?: 0.0,
            crossingCost = obj["crossingCost"]?.jsonPrimitive?.double ?: 0.0,
            carrycostPerDay = obj["carrycostPerDay"]?.jsonPrimitive?.double,
            targetReduction = obj["targetReduction"]?.jsonPrimitive?.double ?: 0.0,
            targetReductionPct = obj["targetReductionPct"]?.jsonPrimitive?.double ?: 0.0,
            residualMetric = obj["residualMetric"]?.jsonPrimitive?.double ?: 0.0,
            liquidityTier = obj["liquidityTier"]?.jsonPrimitive?.content ?: "TIER_1",
            dataQuality = obj["dataQuality"]?.jsonPrimitive?.content ?: "FRESH",
            greekImpact = GreekImpact(
                deltaBefore = greekObj["deltaBefore"]?.jsonPrimitive?.double ?: 0.0,
                deltaAfter = greekObj["deltaAfter"]?.jsonPrimitive?.double ?: 0.0,
                gammaBefore = greekObj["gammaBefore"]?.jsonPrimitive?.double ?: 0.0,
                gammaAfter = greekObj["gammaAfter"]?.jsonPrimitive?.double ?: 0.0,
                vegaBefore = greekObj["vegaBefore"]?.jsonPrimitive?.double ?: 0.0,
                vegaAfter = greekObj["vegaAfter"]?.jsonPrimitive?.double ?: 0.0,
                thetaBefore = greekObj["thetaBefore"]?.jsonPrimitive?.double ?: 0.0,
                thetaAfter = greekObj["thetaAfter"]?.jsonPrimitive?.double ?: 0.0,
                rhoBefore = greekObj["rhoBefore"]?.jsonPrimitive?.double ?: 0.0,
                rhoAfter = greekObj["rhoAfter"]?.jsonPrimitive?.double ?: 0.0,
            ),
        )
    }

    private fun greekImpactToJson(g: GreekImpact) = buildJsonObject {
        put("deltaBefore", g.deltaBefore)
        put("deltaAfter", g.deltaAfter)
        put("gammaBefore", g.gammaBefore)
        put("gammaAfter", g.gammaAfter)
        put("vegaBefore", g.vegaBefore)
        put("vegaAfter", g.vegaAfter)
        put("thetaBefore", g.thetaBefore)
        put("thetaAfter", g.thetaAfter)
        put("rhoBefore", g.rhoBefore)
        put("rhoAfter", g.rhoAfter)
    }

    private fun greekImpactFromJson(obj: JsonObject) = GreekImpact(
        deltaBefore = obj["deltaBefore"]?.jsonPrimitive?.double ?: 0.0,
        deltaAfter = obj["deltaAfter"]?.jsonPrimitive?.double ?: 0.0,
        gammaBefore = obj["gammaBefore"]?.jsonPrimitive?.double ?: 0.0,
        gammaAfter = obj["gammaAfter"]?.jsonPrimitive?.double ?: 0.0,
        vegaBefore = obj["vegaBefore"]?.jsonPrimitive?.double ?: 0.0,
        vegaAfter = obj["vegaAfter"]?.jsonPrimitive?.double ?: 0.0,
        thetaBefore = obj["thetaBefore"]?.jsonPrimitive?.double ?: 0.0,
        thetaAfter = obj["thetaAfter"]?.jsonPrimitive?.double ?: 0.0,
        rhoBefore = obj["rhoBefore"]?.jsonPrimitive?.double ?: 0.0,
        rhoAfter = obj["rhoAfter"]?.jsonPrimitive?.double ?: 0.0,
    )
}

