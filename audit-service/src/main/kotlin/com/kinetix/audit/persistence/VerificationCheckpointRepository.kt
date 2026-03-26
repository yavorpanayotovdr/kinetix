package com.kinetix.audit.persistence

interface VerificationCheckpointRepository {
    suspend fun findLatest(): VerificationCheckpoint?
    suspend fun save(checkpoint: VerificationCheckpoint)
}
