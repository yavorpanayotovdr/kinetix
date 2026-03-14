package com.kinetix.risk.persistence

interface BlobRetentionRepository {
    suspend fun deleteOrphanedBlobs(retentionDays: Long): Int
}
