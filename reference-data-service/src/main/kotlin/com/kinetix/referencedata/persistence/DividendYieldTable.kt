package com.kinetix.referencedata.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object DividendYieldTable : Table("dividend_yields") {
    val instrumentId = varchar("instrument_id", 255)
    val asOfDate = timestampWithTimeZone("as_of_date")
    val yield_ = decimal("yield", 18, 8)
    val exDate = varchar("ex_date", 10).nullable()
    val dataSource = varchar("source", 50)
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(instrumentId, asOfDate)
}
