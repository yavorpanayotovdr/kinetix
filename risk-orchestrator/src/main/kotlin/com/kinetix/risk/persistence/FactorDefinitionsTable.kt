package com.kinetix.risk.persistence

import org.jetbrains.exposed.sql.Table

object FactorDefinitionsTable : Table("factor_definitions") {
    val factorName = varchar("factor_name", 64)
    val proxyInstrumentId = varchar("proxy_instrument_id", 64)
    val description = text("description")

    override val primaryKey = PrimaryKey(factorName)
}
