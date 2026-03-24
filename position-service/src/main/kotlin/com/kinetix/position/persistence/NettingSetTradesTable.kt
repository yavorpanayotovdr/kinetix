package com.kinetix.position.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object NettingSetTradesTable : Table("netting_set_trades") {
    val tradeId = varchar("trade_id", 255)
    val nettingSetId = varchar("netting_set_id", 255)
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(tradeId)
}
