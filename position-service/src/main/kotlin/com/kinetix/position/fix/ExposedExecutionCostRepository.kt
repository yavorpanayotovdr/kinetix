package com.kinetix.position.fix

import com.kinetix.common.model.Side
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExposedExecutionCostRepository(private val db: Database? = null) : ExecutionCostRepository {

    override suspend fun save(analysis: ExecutionCostAnalysis): Unit = newSuspendedTransaction(db = db) {
        ExecutionCostAnalysisTable.insert {
            it[orderId] = analysis.orderId
            it[bookId] = analysis.bookId
            it[instrumentId] = analysis.instrumentId
            it[completedAt] = analysis.completedAt.atOffset(ZoneOffset.UTC)
            it[arrivalPrice] = analysis.arrivalPrice
            it[averageFillPrice] = analysis.averageFillPrice
            it[side] = analysis.side.name
            it[totalQty] = analysis.totalQty
            it[slippageBps] = analysis.metrics.slippageBps
            it[marketImpactBps] = analysis.metrics.marketImpactBps
            it[timingCostBps] = analysis.metrics.timingCostBps
            it[totalCostBps] = analysis.metrics.totalCostBps
            it[createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
    }

    override suspend fun findByOrderId(orderId: String): ExecutionCostAnalysis? = newSuspendedTransaction(db = db) {
        ExecutionCostAnalysisTable
            .selectAll()
            .where { ExecutionCostAnalysisTable.orderId eq orderId }
            .singleOrNull()
            ?.toAnalysis()
    }

    override suspend fun findByBookId(bookId: String): List<ExecutionCostAnalysis> = newSuspendedTransaction(db = db) {
        ExecutionCostAnalysisTable
            .selectAll()
            .where { ExecutionCostAnalysisTable.bookId eq bookId }
            .orderBy(ExecutionCostAnalysisTable.completedAt)
            .map { it.toAnalysis() }
    }

    private fun ResultRow.toAnalysis() = ExecutionCostAnalysis(
        orderId = this[ExecutionCostAnalysisTable.orderId],
        bookId = this[ExecutionCostAnalysisTable.bookId],
        instrumentId = this[ExecutionCostAnalysisTable.instrumentId],
        completedAt = this[ExecutionCostAnalysisTable.completedAt].toInstant(),
        arrivalPrice = this[ExecutionCostAnalysisTable.arrivalPrice],
        averageFillPrice = this[ExecutionCostAnalysisTable.averageFillPrice],
        side = Side.valueOf(this[ExecutionCostAnalysisTable.side]),
        totalQty = this[ExecutionCostAnalysisTable.totalQty],
        metrics = ExecutionCostMetrics(
            slippageBps = this[ExecutionCostAnalysisTable.slippageBps],
            marketImpactBps = this[ExecutionCostAnalysisTable.marketImpactBps],
            timingCostBps = this[ExecutionCostAnalysisTable.timingCostBps],
            totalCostBps = this[ExecutionCostAnalysisTable.totalCostBps],
        ),
    )
}
