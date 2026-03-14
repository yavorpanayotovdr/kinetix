package com.kinetix.risk.persistence

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ExposedBlobRetentionRepository(private val db: Database? = null) : BlobRetentionRepository {

    override suspend fun deleteOrphanedBlobs(retentionDays: Long): Int = newSuspendedTransaction(db = db) {
        @Suppress("SqlInjection")
        val sql = """
            DELETE FROM run_market_data_blobs
            WHERE content_hash NOT IN (
                SELECT content_hash FROM run_manifest_market_data
                WHERE manifest_id IN (
                    SELECT manifest_id FROM run_manifests
                    WHERE captured_at > NOW() - INTERVAL '$retentionDays days'
                )
            )
            AND created_at < NOW() - INTERVAL '$retentionDays days'
        """.trimIndent()

        connection.prepareStatement(sql, false).executeUpdate()
    }
}
