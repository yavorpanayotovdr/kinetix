package com.kinetix.regulatory.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object FrtbCalculationsTable : Table("frtb_calculations") {
    val id = varchar("id", 255)
    val portfolioId = varchar("portfolio_id", 255)
    val totalSbmCharge = double("total_sbm_charge")
    val grossJtd = double("gross_jtd")
    val hedgeBenefit = double("hedge_benefit")
    val netDrc = double("net_drc")
    val exoticNotional = double("exotic_notional")
    val otherNotional = double("other_notional")
    val totalRrao = double("total_rrao")
    val totalCapitalCharge = double("total_capital_charge")
    val sbmChargesJson = text("sbm_charges_json")
    val calculatedAt = timestampWithTimeZone("calculated_at")
    val storedAt = timestampWithTimeZone("stored_at")

    override val primaryKey = PrimaryKey(id)
}
