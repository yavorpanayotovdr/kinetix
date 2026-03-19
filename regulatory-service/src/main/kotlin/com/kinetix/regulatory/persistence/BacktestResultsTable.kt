package com.kinetix.regulatory.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object BacktestResultsTable : Table("backtest_results") {
    val id = varchar("id", 255)
    val bookId = varchar("book_id", 255)
    val calculationType = varchar("calculation_type", 50)
    val confidenceLevel = double("confidence_level")
    val totalDays = integer("total_days")
    val violationCount = integer("violation_count")
    val violationRate = double("violation_rate")
    val kupiecStatistic = double("kupiec_statistic")
    val kupiecPValue = double("kupiec_p_value")
    val kupiecPass = bool("kupiec_pass")
    val christoffersenStatistic = double("christoffersen_statistic")
    val christoffersenPValue = double("christoffersen_p_value")
    val christoffersenPass = bool("christoffersen_pass")
    val trafficLightZone = varchar("traffic_light_zone", 10)
    val calculatedAt = timestampWithTimeZone("calculated_at")
    val inputDigest = char("input_digest", 64).nullable()
    val windowStart = date("window_start").nullable()
    val windowEnd = date("window_end").nullable()
    val modelVersion = varchar("model_version", 100).nullable()

    override val primaryKey = PrimaryKey(id)
}
