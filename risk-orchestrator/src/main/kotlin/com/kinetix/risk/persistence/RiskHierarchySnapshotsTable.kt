package com.kinetix.risk.persistence

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object RiskHierarchySnapshotsTable : Table("risk_hierarchy_snapshots") {
    val id = long("id").autoIncrement()
    val snapshotAt = timestampWithTimeZone("snapshot_at")
    val level = varchar("level", 16)
    val entityId = varchar("entity_id", 64)
    val parentId = varchar("parent_id", 64).nullable()
    val varValue = decimal("var_value", 24, 6)
    val expectedShortfall = decimal("expected_shortfall", 24, 6).nullable()
    val pnlToday = decimal("pnl_today", 24, 6).nullable()
    val limitUtilisation = decimal("limit_utilisation", 10, 6).nullable()
    val marginalVar = decimal("marginal_var", 24, 6).nullable()
    val topContributors = jsonb<JsonElement>("top_contributors", Json)
    val isPartial = bool("is_partial")

    override val primaryKey = PrimaryKey(id, snapshotAt)
}
