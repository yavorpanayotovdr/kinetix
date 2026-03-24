package com.kinetix.position.fix

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object ExecutionFillsTable : Table("execution_fills") {
    val fillId = varchar("fill_id", 255)
    val orderId = varchar("order_id", 255)
    val bookId = varchar("book_id", 255)
    val instrumentId = varchar("instrument_id", 255)
    val fillTime = timestampWithTimeZone("fill_time")
    val fillQty = decimal("fill_qty", 28, 12)
    val fillPrice = decimal("fill_price", 28, 12)
    val fillType = varchar("fill_type", 20)
    val venue = varchar("venue", 100).nullable()
    val cumulativeQty = decimal("cumulative_qty", 28, 12)
    val averagePrice = decimal("average_price", 28, 12)
    val fixExecId = varchar("fix_exec_id", 255).nullable()
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(fillId)
}
