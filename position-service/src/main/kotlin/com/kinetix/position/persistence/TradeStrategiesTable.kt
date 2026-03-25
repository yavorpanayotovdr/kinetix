package com.kinetix.position.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object TradeStrategiesTable : Table("trade_strategies") {
    val strategyId = varchar("strategy_id", 36)
    val bookId = varchar("book_id", 255)
    val strategyType = varchar("strategy_type", 50)
    val name = varchar("name", 255).nullable()
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(strategyId)
}
