package com.kinetix.audit.persistence

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExposedVerificationCheckpointRepository(
    private val db: Database? = null,
) : VerificationCheckpointRepository {

    override suspend fun findLatest(): VerificationCheckpoint? = newSuspendedTransaction(db = db) {
        VerificationCheckpointsTable
            .selectAll()
            .orderBy(VerificationCheckpointsTable.verifiedAt, SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.let { row ->
                VerificationCheckpoint(
                    id = row[VerificationCheckpointsTable.id],
                    lastEventId = row[VerificationCheckpointsTable.lastEventId],
                    lastHash = row[VerificationCheckpointsTable.lastHash],
                    eventCount = row[VerificationCheckpointsTable.eventCount],
                    verifiedAt = row[VerificationCheckpointsTable.verifiedAt].toInstant(),
                )
            }
    }

    override suspend fun save(checkpoint: VerificationCheckpoint): Unit = newSuspendedTransaction(db = db) {
        VerificationCheckpointsTable.insert {
            it[lastEventId] = checkpoint.lastEventId
            it[lastHash] = checkpoint.lastHash
            it[eventCount] = checkpoint.eventCount
            it[verifiedAt] = OffsetDateTime.ofInstant(
                checkpoint.verifiedAt.takeIf { ts -> ts != Instant.EPOCH } ?: Instant.now(),
                ZoneOffset.UTC,
            )
        }
    }
}
