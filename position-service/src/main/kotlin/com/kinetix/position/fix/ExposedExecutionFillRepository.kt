package com.kinetix.position.fix

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExposedExecutionFillRepository(private val db: Database? = null) : ExecutionFillRepository {

    override suspend fun save(fill: ExecutionFill): Unit = newSuspendedTransaction(db = db) {
        ExecutionFillsTable.insert {
            it[fillId] = fill.fillId
            it[orderId] = fill.orderId
            it[bookId] = fill.bookId
            it[instrumentId] = fill.instrumentId
            it[fillTime] = fill.fillTime.atOffset(ZoneOffset.UTC)
            it[fillQty] = fill.fillQty
            it[fillPrice] = fill.fillPrice
            it[fillType] = fill.fillType.name
            it[venue] = fill.venue
            it[cumulativeQty] = fill.cumulativeQty
            it[averagePrice] = fill.averagePrice
            it[fixExecId] = fill.fixExecId
            it[createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
    }

    override suspend fun findByOrderId(orderId: String): List<ExecutionFill> = newSuspendedTransaction(db = db) {
        ExecutionFillsTable
            .selectAll()
            .where { ExecutionFillsTable.orderId eq orderId }
            .orderBy(ExecutionFillsTable.fillTime)
            .map { it.toFill() }
    }

    override suspend fun existsByFixExecId(fixExecId: String): Boolean = newSuspendedTransaction(db = db) {
        ExecutionFillsTable
            .selectAll()
            .where { ExecutionFillsTable.fixExecId eq fixExecId }
            .count() > 0
    }

    private fun ResultRow.toFill() = ExecutionFill(
        fillId = this[ExecutionFillsTable.fillId],
        orderId = this[ExecutionFillsTable.orderId],
        bookId = this[ExecutionFillsTable.bookId],
        instrumentId = this[ExecutionFillsTable.instrumentId],
        fillTime = this[ExecutionFillsTable.fillTime].toInstant(),
        fillQty = this[ExecutionFillsTable.fillQty],
        fillPrice = this[ExecutionFillsTable.fillPrice],
        fillType = FillType.valueOf(this[ExecutionFillsTable.fillType]),
        venue = this[ExecutionFillsTable.venue],
        cumulativeQty = this[ExecutionFillsTable.cumulativeQty],
        averagePrice = this[ExecutionFillsTable.averagePrice],
        fixExecId = this[ExecutionFillsTable.fixExecId],
    )
}
