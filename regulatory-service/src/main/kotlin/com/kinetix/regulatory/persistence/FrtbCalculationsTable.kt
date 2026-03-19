package com.kinetix.regulatory.persistence

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object FrtbCalculationsTable : Table("frtb_calculations") {
    val id = varchar("id", 255)
    val portfolioId = varchar("portfolio_id", 255)
    val totalSbmCharge = decimal("total_sbm_charge", 28, 8)
    val grossJtd = decimal("gross_jtd", 28, 8)
    val hedgeBenefit = decimal("hedge_benefit", 28, 8)
    val netDrc = decimal("net_drc", 28, 8)
    val exoticNotional = decimal("exotic_notional", 28, 8)
    val otherNotional = decimal("other_notional", 28, 8)
    val totalRrao = decimal("total_rrao", 28, 8)
    val totalCapitalCharge = decimal("total_capital_charge", 28, 8)
    val sbmChargesJson = jsonb<JsonElement>("sbm_charges_json", Json)
    val calculatedAt = timestampWithTimeZone("calculated_at")
    val storedAt = timestampWithTimeZone("stored_at")

    override val primaryKey = PrimaryKey(id)
}
