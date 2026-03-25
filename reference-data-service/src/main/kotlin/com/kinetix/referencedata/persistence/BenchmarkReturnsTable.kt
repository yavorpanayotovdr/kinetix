package com.kinetix.referencedata.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.date

object BenchmarkReturnsTable : Table("benchmark_returns") {
    val benchmarkId = varchar("benchmark_id", 36)
    val returnDate = date("return_date")
    val dailyReturn = decimal("daily_return", precision = 18, scale = 10)

    override val primaryKey = PrimaryKey(benchmarkId, returnDate)
}
