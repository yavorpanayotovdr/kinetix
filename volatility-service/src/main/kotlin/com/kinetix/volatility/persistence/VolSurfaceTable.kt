package com.kinetix.volatility.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object VolSurfaceTable : Table("volatility_surfaces") {
    val instrumentId = varchar("instrument_id", 255)
    val asOfDate = timestampWithTimeZone("as_of_date")
    val dataSource = varchar("source", 50)
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(instrumentId, asOfDate)
}

object VolPointTable : Table("volatility_surface_points") {
    val instrumentId = varchar("instrument_id", 255)
    val asOfDate = timestampWithTimeZone("as_of_date")
    val strike = decimal("strike", 28, 12)
    val maturityDays = integer("maturity_days")
    val impliedVol = decimal("implied_vol", 18, 8)

    override val primaryKey = PrimaryKey(instrumentId, asOfDate, strike, maturityDays)
}
