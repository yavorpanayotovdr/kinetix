package com.kinetix.position.persistence

import com.kinetix.position.model.CollateralBalance
import com.kinetix.position.model.CollateralDirection
import com.kinetix.position.model.CollateralType
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExposedCollateralBalanceRepository(
    private val db: Database? = null,
) : CollateralBalanceRepository {

    override suspend fun save(balance: CollateralBalance): CollateralBalance =
        newSuspendedTransaction(db = db) {
            val now = OffsetDateTime.now(ZoneOffset.UTC)
            val result = CollateralBalancesTable.insert {
                it[counterpartyId] = balance.counterpartyId
                it[nettingSetId] = balance.nettingSetId
                it[collateralType] = balance.collateralType.name
                it[amount] = balance.amount
                it[currency] = balance.currency
                it[direction] = balance.direction.name
                it[asOfDate] = kotlinx.datetime.LocalDate(
                    balance.asOfDate.year,
                    balance.asOfDate.monthValue,
                    balance.asOfDate.dayOfMonth,
                )
                it[valueAfterHaircut] = balance.valueAfterHaircut
                it[createdAt] = now
                it[updatedAt] = now
            }
            balance.copy(id = result[CollateralBalancesTable.id])
        }

    override suspend fun findByCounterpartyId(counterpartyId: String): List<CollateralBalance> =
        newSuspendedTransaction(db = db) {
            CollateralBalancesTable
                .selectAll()
                .where { CollateralBalancesTable.counterpartyId eq counterpartyId }
                .map { it.toCollateralBalance() }
        }

    override suspend fun findByNettingSetId(nettingSetId: String): List<CollateralBalance> =
        newSuspendedTransaction(db = db) {
            CollateralBalancesTable
                .selectAll()
                .where { CollateralBalancesTable.nettingSetId eq nettingSetId }
                .map { it.toCollateralBalance() }
        }

    override suspend fun deleteById(id: Long): Unit =
        newSuspendedTransaction(db = db) {
            CollateralBalancesTable.deleteWhere { CollateralBalancesTable.id eq id }
        }

    private fun ResultRow.toCollateralBalance() = CollateralBalance(
        id = this[CollateralBalancesTable.id],
        counterpartyId = this[CollateralBalancesTable.counterpartyId],
        nettingSetId = this[CollateralBalancesTable.nettingSetId],
        collateralType = CollateralType.valueOf(this[CollateralBalancesTable.collateralType]),
        amount = this[CollateralBalancesTable.amount],
        currency = this[CollateralBalancesTable.currency],
        direction = CollateralDirection.valueOf(this[CollateralBalancesTable.direction]),
        asOfDate = this[CollateralBalancesTable.asOfDate].let {
            java.time.LocalDate.of(it.year, it.monthNumber, it.dayOfMonth)
        },
        valueAfterHaircut = this[CollateralBalancesTable.valueAfterHaircut],
        createdAt = this[CollateralBalancesTable.createdAt].toInstant(),
        updatedAt = this[CollateralBalancesTable.updatedAt].toInstant(),
    )
}
