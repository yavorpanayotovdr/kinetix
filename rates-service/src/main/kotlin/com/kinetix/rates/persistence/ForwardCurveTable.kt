package com.kinetix.rates.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object ForwardCurveTable : Table("forward_curves") {
    val instrumentId = varchar("instrument_id", 255)
    val asOfDate = timestampWithTimeZone("as_of_date")
    val assetClass = varchar("asset_class", 50)
    val dataSource = varchar("source", 50)
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(instrumentId, asOfDate)
}

object ForwardCurvePointTable : Table("forward_curve_points") {
    val instrumentId = varchar("instrument_id", 255)
    val asOfDate = timestampWithTimeZone("as_of_date")
    val tenor = varchar("tenor", 50)
    val value = decimal("value", 28, 12)

    override val primaryKey = PrimaryKey(instrumentId, asOfDate, tenor)
}
