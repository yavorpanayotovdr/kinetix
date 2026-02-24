package com.kinetix.rates.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object YieldCurveTable : Table("yield_curves") {
    val curveId = varchar("curve_id", 255)
    val asOfDate = timestampWithTimeZone("as_of_date")
    val currency = varchar("currency", 3)
    val dataSource = varchar("source", 50)
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(curveId, asOfDate)
}

object YieldCurveTenorTable : Table("yield_curve_tenors") {
    val curveId = varchar("curve_id", 255)
    val asOfDate = timestampWithTimeZone("as_of_date")
    val label = varchar("label", 50)
    val days = integer("days")
    val rate = decimal("rate", 28, 12)

    override val primaryKey = PrimaryKey(curveId, asOfDate, label)
}
