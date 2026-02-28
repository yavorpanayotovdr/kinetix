package com.kinetix.risk.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object SodBaselinesTable : Table("sod_baselines") {
    val id = long("id").autoIncrement()
    val portfolioId = varchar("portfolio_id", 64)
    val baselineDate = date("baseline_date")
    val snapshotType = varchar("snapshot_type", 16)
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}
