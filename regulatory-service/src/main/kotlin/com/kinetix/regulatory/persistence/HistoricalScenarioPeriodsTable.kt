package com.kinetix.regulatory.persistence

import org.jetbrains.exposed.sql.Table

object HistoricalScenarioPeriodsTable : Table("historical_scenario_periods") {
    val periodId = varchar("period_id", 255)
    val name = varchar("name", 255)
    val description = text("description").nullable()
    val startDate = varchar("start_date", 10)
    val endDate = varchar("end_date", 10)
    val assetClassFocus = varchar("asset_class_focus", 100).nullable()
    val severityLabel = varchar("severity_label", 50).nullable()

    override val primaryKey = PrimaryKey(periodId)
}
