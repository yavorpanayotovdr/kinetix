package com.kinetix.referencedata.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object CreditSpreadTable : Table("credit_spreads") {
    val instrumentId = varchar("instrument_id", 255)
    val asOfDate = timestampWithTimeZone("as_of_date")
    val spread = decimal("spread", 18, 8)
    val rating = varchar("rating", 20).nullable()
    val dataSource = varchar("source", 50)
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(instrumentId, asOfDate)
}
