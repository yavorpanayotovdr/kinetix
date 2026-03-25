package com.kinetix.referencedata.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object BenchmarksTable : Table("benchmarks") {
    val benchmarkId = varchar("benchmark_id", 36)
    val name = varchar("name", 255)
    val description = text("description").nullable()
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(benchmarkId)
}
