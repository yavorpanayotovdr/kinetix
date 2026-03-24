package com.kinetix.position.fix

import com.kinetix.common.model.Side
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExposedExecutionOrderRepository(private val db: Database? = null) : ExecutionOrderRepository {

    override suspend fun save(order: Order): Unit = newSuspendedTransaction(db = db) {
        ExecutionOrdersTable.insert {
            it[orderId] = order.orderId
            it[bookId] = order.bookId
            it[instrumentId] = order.instrumentId
            it[side] = order.side.name
            it[quantity] = order.quantity
            it[orderType] = order.orderType
            it[limitPrice] = order.limitPrice
            it[arrivalPrice] = order.arrivalPrice
            it[submittedAt] = order.submittedAt.atOffset(ZoneOffset.UTC)
            it[status] = order.status.name
            it[riskCheckResult] = order.riskCheckResult
            it[riskCheckDetails] = order.riskCheckDetails
            it[fixSessionId] = order.fixSessionId
            it[createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
            it[updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
    }

    override suspend fun updateStatus(
        orderId: String,
        status: OrderStatus,
        riskCheckResult: String?,
        riskCheckDetails: String?,
    ): Unit = newSuspendedTransaction(db = db) {
        ExecutionOrdersTable.update({ ExecutionOrdersTable.orderId eq orderId }) {
            it[ExecutionOrdersTable.status] = status.name
            it[ExecutionOrdersTable.riskCheckResult] = riskCheckResult
            it[ExecutionOrdersTable.riskCheckDetails] = riskCheckDetails
            it[updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
    }

    override suspend fun findById(orderId: String): Order? = newSuspendedTransaction(db = db) {
        ExecutionOrdersTable
            .selectAll()
            .where { ExecutionOrdersTable.orderId eq orderId }
            .singleOrNull()
            ?.toOrder()
    }

    override suspend fun findByBookId(bookId: String): List<Order> = newSuspendedTransaction(db = db) {
        ExecutionOrdersTable
            .selectAll()
            .where { ExecutionOrdersTable.bookId eq bookId }
            .orderBy(ExecutionOrdersTable.submittedAt)
            .map { it.toOrder() }
    }

    private fun ResultRow.toOrder() = Order(
        orderId = this[ExecutionOrdersTable.orderId],
        bookId = this[ExecutionOrdersTable.bookId],
        instrumentId = this[ExecutionOrdersTable.instrumentId],
        side = Side.valueOf(this[ExecutionOrdersTable.side]),
        quantity = this[ExecutionOrdersTable.quantity],
        orderType = this[ExecutionOrdersTable.orderType],
        limitPrice = this[ExecutionOrdersTable.limitPrice],
        arrivalPrice = this[ExecutionOrdersTable.arrivalPrice],
        submittedAt = this[ExecutionOrdersTable.submittedAt].toInstant(),
        status = OrderStatus.valueOf(this[ExecutionOrdersTable.status]),
        riskCheckResult = this[ExecutionOrdersTable.riskCheckResult],
        riskCheckDetails = this[ExecutionOrdersTable.riskCheckDetails],
        fixSessionId = this[ExecutionOrdersTable.fixSessionId],
    )
}
