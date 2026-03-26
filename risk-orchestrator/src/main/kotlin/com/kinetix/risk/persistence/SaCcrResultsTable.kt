package com.kinetix.risk.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object SaCcrResultsTable : Table("sa_ccr_results") {
    val id = long("id").autoIncrement()
    val counterpartyId = varchar("counterparty_id", 255)
    val nettingSetId = varchar("netting_set_id", 255)
    val calculatedAt = timestampWithTimeZone("calculated_at")
    val replacementCost = decimal("replacement_cost", 24, 6)
    val pfeAddon = decimal("pfe_addon", 24, 6)
    val multiplier = decimal("multiplier", 10, 8)
    val ead = decimal("ead", 24, 6)
    val alpha = decimal("alpha", 6, 4)
    val collateralNet = decimal("collateral_net", 24, 6)
    val positionCount = integer("position_count")

    override val primaryKey = PrimaryKey(id, calculatedAt)
}
