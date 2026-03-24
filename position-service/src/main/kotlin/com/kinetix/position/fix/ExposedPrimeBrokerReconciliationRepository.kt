package com.kinetix.position.fix

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExposedPrimeBrokerReconciliationRepository(private val db: Database? = null) : PrimeBrokerReconciliationRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun save(reconciliation: PrimeBrokerReconciliation, id: String): Unit =
        newSuspendedTransaction(db = db) {
            PrimeBrokerReconciliationTable.insert {
                it[PrimeBrokerReconciliationTable.id] = id
                it[reconciliationDate] = reconciliation.reconciliationDate
                it[bookId] = reconciliation.bookId
                it[status] = reconciliation.status
                it[totalPositions] = reconciliation.totalPositions
                it[matchedCount] = reconciliation.matchedCount
                it[breakCount] = reconciliation.breakCount
                it[breaks] = json.encodeToString(reconciliation.breaks.map { br -> br.toDto() })
                it[reconciledAt] = reconciliation.reconciledAt.atOffset(ZoneOffset.UTC)
                it[createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
            }
        }

    override suspend fun findByBookId(bookId: String): List<PrimeBrokerReconciliation> =
        newSuspendedTransaction(db = db) {
            PrimeBrokerReconciliationTable
                .selectAll()
                .where { PrimeBrokerReconciliationTable.bookId eq bookId }
                .orderBy(PrimeBrokerReconciliationTable.reconciliationDate, SortOrder.DESC)
                .map { it.toReconciliation() }
        }

    override suspend fun findLatestByBookId(bookId: String): PrimeBrokerReconciliation? =
        newSuspendedTransaction(db = db) {
            PrimeBrokerReconciliationTable
                .selectAll()
                .where { PrimeBrokerReconciliationTable.bookId eq bookId }
                .orderBy(PrimeBrokerReconciliationTable.reconciliationDate, SortOrder.DESC)
                .firstOrNull()
                ?.toReconciliation()
        }

    private fun ResultRow.toReconciliation(): PrimeBrokerReconciliation {
        val breaksJson = this[PrimeBrokerReconciliationTable.breaks]
        val breaks = json.decodeFromString<List<ReconciliationBreakDto>>(breaksJson)
            .map { it.toDomain() }
        return PrimeBrokerReconciliation(
            reconciliationDate = this[PrimeBrokerReconciliationTable.reconciliationDate],
            bookId = this[PrimeBrokerReconciliationTable.bookId],
            status = this[PrimeBrokerReconciliationTable.status],
            totalPositions = this[PrimeBrokerReconciliationTable.totalPositions],
            matchedCount = this[PrimeBrokerReconciliationTable.matchedCount],
            breakCount = this[PrimeBrokerReconciliationTable.breakCount],
            breaks = breaks,
            reconciledAt = this[PrimeBrokerReconciliationTable.reconciledAt].toInstant(),
        )
    }
}

@Serializable
private data class ReconciliationBreakDto(
    val instrumentId: String,
    val internalQty: String,
    val primeBrokerQty: String,
    val breakQty: String,
    val breakNotional: String,
)

private fun ReconciliationBreak.toDto() = ReconciliationBreakDto(
    instrumentId = instrumentId,
    internalQty = internalQty.toPlainString(),
    primeBrokerQty = primeBrokerQty.toPlainString(),
    breakQty = breakQty.toPlainString(),
    breakNotional = breakNotional.toPlainString(),
)

private fun ReconciliationBreakDto.toDomain() = ReconciliationBreak(
    instrumentId = instrumentId,
    internalQty = BigDecimal(internalQty),
    primeBrokerQty = BigDecimal(primeBrokerQty),
    breakQty = BigDecimal(breakQty),
    breakNotional = BigDecimal(breakNotional),
)
