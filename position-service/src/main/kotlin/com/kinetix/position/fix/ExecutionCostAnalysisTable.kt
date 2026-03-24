package com.kinetix.position.fix

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object ExecutionCostAnalysisTable : Table("execution_cost_analysis") {
    val orderId = varchar("order_id", 255)
    val bookId = varchar("book_id", 255)
    val instrumentId = varchar("instrument_id", 255)
    val completedAt = timestampWithTimeZone("completed_at")
    val arrivalPrice = decimal("arrival_price", 28, 12)
    val averageFillPrice = decimal("average_fill_price", 28, 12)
    val side = varchar("side", 10)
    val totalQty = decimal("total_qty", 28, 12)
    val slippageBps = decimal("slippage_bps", 20, 10)
    val marketImpactBps = decimal("market_impact_bps", 20, 10).nullable()
    val timingCostBps = decimal("timing_cost_bps", 20, 10).nullable()
    val totalCostBps = decimal("total_cost_bps", 20, 10)
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(orderId)
}
