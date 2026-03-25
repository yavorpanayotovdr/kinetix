package com.kinetix.regulatory.persistence

import org.jetbrains.exposed.sql.Table

object HistoricalScenarioReturnsTable : Table("historical_scenario_returns") {
    val periodId = varchar("period_id", 255)
    val instrumentId = varchar("instrument_id", 255)
    val returnDate = varchar("return_date", 10)
    val dailyReturn = decimal("daily_return", 18, 8)
    val returnSource = varchar("source", 100).default("HISTORICAL")

    override val primaryKey = PrimaryKey(periodId, instrumentId, returnDate)
}
