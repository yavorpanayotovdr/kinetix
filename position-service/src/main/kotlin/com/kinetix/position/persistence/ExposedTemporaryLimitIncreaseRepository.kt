package com.kinetix.position.persistence

import com.kinetix.position.model.TemporaryLimitIncrease
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExposedTemporaryLimitIncreaseRepository(private val db: Database? = null) : TemporaryLimitIncreaseRepository {

    override suspend fun findActiveByLimitId(
        limitId: String,
        asOf: Instant,
    ): TemporaryLimitIncrease? = newSuspendedTransaction(db = db) {
        val asOfOffset = OffsetDateTime.ofInstant(asOf, ZoneOffset.UTC)
        LimitTemporaryIncreasesTable
            .selectAll()
            .where {
                (LimitTemporaryIncreasesTable.limitId eq limitId) and
                    (LimitTemporaryIncreasesTable.expiresAt greater asOfOffset)
            }
            .orderBy(LimitTemporaryIncreasesTable.createdAt)
            .lastOrNull()
            ?.toModel()
    }

    override suspend fun save(temporaryLimitIncrease: TemporaryLimitIncrease): Unit = newSuspendedTransaction(db = db) {
        LimitTemporaryIncreasesTable.insert {
            it[id] = temporaryLimitIncrease.id
            it[limitId] = temporaryLimitIncrease.limitId
            it[newValue] = temporaryLimitIncrease.newValue
            it[approvedBy] = temporaryLimitIncrease.approvedBy
            it[expiresAt] = OffsetDateTime.ofInstant(temporaryLimitIncrease.expiresAt, ZoneOffset.UTC)
            it[reason] = temporaryLimitIncrease.reason
            it[createdAt] = OffsetDateTime.ofInstant(temporaryLimitIncrease.createdAt, ZoneOffset.UTC)
        }
    }

    private fun ResultRow.toModel(): TemporaryLimitIncrease = TemporaryLimitIncrease(
        id = this[LimitTemporaryIncreasesTable.id],
        limitId = this[LimitTemporaryIncreasesTable.limitId],
        newValue = this[LimitTemporaryIncreasesTable.newValue],
        approvedBy = this[LimitTemporaryIncreasesTable.approvedBy],
        expiresAt = this[LimitTemporaryIncreasesTable.expiresAt].toInstant(),
        reason = this[LimitTemporaryIncreasesTable.reason],
        createdAt = this[LimitTemporaryIncreasesTable.createdAt].toInstant(),
    )
}
