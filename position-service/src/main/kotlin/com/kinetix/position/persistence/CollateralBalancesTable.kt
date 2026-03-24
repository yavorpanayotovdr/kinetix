package com.kinetix.position.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object CollateralBalancesTable : Table("collateral_balances") {
    val id = long("id").autoIncrement()
    val counterpartyId = varchar("counterparty_id", 255)
    val nettingSetId = varchar("netting_set_id", 255).nullable()
    val collateralType = varchar("collateral_type", 30)
    val amount = decimal("amount", 24, 6)
    val currency = varchar("currency", 3).default("USD")
    val direction = varchar("direction", 10)
    val asOfDate = date("as_of_date")
    val valueAfterHaircut = decimal("value_after_haircut", 24, 6)
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}
