package com.kinetix.risk.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object OfficialEodDesignationsTable : Table("official_eod_designations") {
    val portfolioId = varchar("portfolio_id", 255)
    val valuationDate = date("valuation_date")
    val jobId = uuid("job_id")
    val promotedAt = timestampWithTimeZone("promoted_at")
    val promotedBy = varchar("promoted_by", 255)

    override val primaryKey = PrimaryKey(portfolioId, valuationDate)
}
