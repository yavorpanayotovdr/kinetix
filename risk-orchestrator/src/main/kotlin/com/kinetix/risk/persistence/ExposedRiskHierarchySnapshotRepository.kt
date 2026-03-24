package com.kinetix.risk.persistence

import com.kinetix.risk.model.HierarchyLevel
import com.kinetix.risk.model.HierarchyRiskSnapshot
import com.kinetix.risk.model.RiskContributor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.math.BigDecimal
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset


class ExposedRiskHierarchySnapshotRepository(
    private val db: Database? = null,
) : RiskHierarchySnapshotRepository {

    override suspend fun save(snapshot: HierarchyRiskSnapshot): Unit = newSuspendedTransaction(db = db) {
        RiskHierarchySnapshotsTable.insert {
            it[snapshotAt] = snapshot.snapshotAt.toOffsetUtc()
            it[level] = snapshot.level.name
            it[entityId] = snapshot.entityId
            it[parentId] = snapshot.parentId
            it[varValue] = BigDecimal.valueOf(snapshot.varValue)
            it[expectedShortfall] = snapshot.expectedShortfall?.let { v -> BigDecimal.valueOf(v) }
            it[pnlToday] = snapshot.pnlToday?.let { v -> BigDecimal.valueOf(v) }
            it[limitUtilisation] = snapshot.limitUtilisation?.let { v -> BigDecimal.valueOf(v) }
            it[marginalVar] = snapshot.marginalVar?.let { v -> BigDecimal.valueOf(v) }
            it[topContributors] = serializeContributors(snapshot.topContributors)
            it[isPartial] = snapshot.isPartial
        }
    }

    override suspend fun findLatest(level: HierarchyLevel, entityId: String): HierarchyRiskSnapshot? =
        newSuspendedTransaction(db = db) {
            RiskHierarchySnapshotsTable
                .selectAll()
                .where {
                    (RiskHierarchySnapshotsTable.level eq level.name) and
                        (RiskHierarchySnapshotsTable.entityId eq entityId)
                }
                .orderBy(RiskHierarchySnapshotsTable.snapshotAt, SortOrder.DESC)
                .limit(1)
                .singleOrNull()
                ?.let { row ->
                    HierarchyRiskSnapshot(
                        snapshotAt = row[RiskHierarchySnapshotsTable.snapshotAt].toInstant(),
                        level = HierarchyLevel.valueOf(row[RiskHierarchySnapshotsTable.level]),
                        entityId = row[RiskHierarchySnapshotsTable.entityId],
                        parentId = row[RiskHierarchySnapshotsTable.parentId],
                        varValue = row[RiskHierarchySnapshotsTable.varValue].toDouble(),
                        expectedShortfall = row[RiskHierarchySnapshotsTable.expectedShortfall]?.toDouble(),
                        pnlToday = row[RiskHierarchySnapshotsTable.pnlToday]?.toDouble(),
                        limitUtilisation = row[RiskHierarchySnapshotsTable.limitUtilisation]?.toDouble(),
                        marginalVar = row[RiskHierarchySnapshotsTable.marginalVar]?.toDouble(),
                        topContributors = deserializeContributors(row[RiskHierarchySnapshotsTable.topContributors]),
                        isPartial = row[RiskHierarchySnapshotsTable.isPartial],
                    )
                }
        }

    override suspend fun findHistory(
        level: HierarchyLevel,
        entityId: String,
        limit: Int,
    ): List<HierarchyRiskSnapshot> = newSuspendedTransaction(db = db) {
        RiskHierarchySnapshotsTable
            .selectAll()
            .where {
                (RiskHierarchySnapshotsTable.level eq level.name) and
                    (RiskHierarchySnapshotsTable.entityId eq entityId)
            }
            .orderBy(RiskHierarchySnapshotsTable.snapshotAt, SortOrder.DESC)
            .limit(limit)
            .map { row ->
                HierarchyRiskSnapshot(
                    snapshotAt = row[RiskHierarchySnapshotsTable.snapshotAt].toInstant(),
                    level = HierarchyLevel.valueOf(row[RiskHierarchySnapshotsTable.level]),
                    entityId = row[RiskHierarchySnapshotsTable.entityId],
                    parentId = row[RiskHierarchySnapshotsTable.parentId],
                    varValue = row[RiskHierarchySnapshotsTable.varValue].toDouble(),
                    expectedShortfall = row[RiskHierarchySnapshotsTable.expectedShortfall]?.toDouble(),
                    pnlToday = row[RiskHierarchySnapshotsTable.pnlToday]?.toDouble(),
                    limitUtilisation = row[RiskHierarchySnapshotsTable.limitUtilisation]?.toDouble(),
                    marginalVar = row[RiskHierarchySnapshotsTable.marginalVar]?.toDouble(),
                    topContributors = deserializeContributors(row[RiskHierarchySnapshotsTable.topContributors]),
                    isPartial = row[RiskHierarchySnapshotsTable.isPartial],
                )
            }
    }

    private fun serializeContributors(contributors: List<RiskContributor>): JsonElement =
        buildJsonArray {
            contributors.forEach { c ->
                add(buildJsonObject {
                    put("entityId", c.entityId)
                    put("entityName", c.entityName)
                    put("varContribution", c.varContribution)
                    put("pctOfTotal", c.pctOfTotal)
                })
            }
        }

    private fun deserializeContributors(json: JsonElement): List<RiskContributor> {
        val arr = json as? JsonArray ?: return emptyList()
        return arr.mapNotNull { elem ->
            val obj = elem as? JsonObject ?: return@mapNotNull null
            RiskContributor(
                entityId = obj["entityId"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                entityName = obj["entityName"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                varContribution = obj["varContribution"]?.jsonPrimitive?.double ?: 0.0,
                pctOfTotal = obj["pctOfTotal"]?.jsonPrimitive?.double ?: 0.0,
            )
        }
    }

    private fun Instant.toOffsetUtc(): OffsetDateTime = OffsetDateTime.ofInstant(this, ZoneOffset.UTC)
}
