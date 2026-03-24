package com.kinetix.risk.persistence

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone
import java.util.UUID

object FactorDecompositionSnapshotsTable : Table("factor_decomposition_snapshots") {
    val snapshotId = uuid("snapshot_id").clientDefault { UUID.randomUUID() }
    val bookId = varchar("book_id", 255)
    val calculatedAt = timestampWithTimeZone("calculated_at")
    val totalVar = decimal("total_var", 24, 6)
    val systematicVar = decimal("systematic_var", 24, 6)
    val idiosyncraticVar = decimal("idiosyncratic_var", 24, 6)
    val rSquared = decimal("r_squared", 8, 6)
    val concentrationWarning = bool("concentration_warning")
    val factorsJson = jsonb<JsonElement>("factors_json", Json)

    override val primaryKey = PrimaryKey(snapshotId)
}
