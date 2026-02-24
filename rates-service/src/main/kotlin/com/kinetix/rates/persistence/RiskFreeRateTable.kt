package com.kinetix.rates.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object RiskFreeRateTable : Table("risk_free_rates") {
    val currency = varchar("currency", 3)
    val tenor = varchar("tenor", 50)
    val asOfDate = timestampWithTimeZone("as_of_date")
    val rate = decimal("rate", 28, 12)
    val dataSource = varchar("source", 50)
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(currency, tenor, asOfDate)
}
