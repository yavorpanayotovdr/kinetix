package com.kinetix.referencedata.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.date

object BenchmarkConstituentsTable : Table("benchmark_constituents") {
    val benchmarkId = varchar("benchmark_id", 36)
    val instrumentId = varchar("instrument_id", 255)
    val weight = decimal("weight", precision = 18, scale = 10)
    val asOfDate = date("as_of_date")

    override val primaryKey = PrimaryKey(benchmarkId, instrumentId, asOfDate)
}
