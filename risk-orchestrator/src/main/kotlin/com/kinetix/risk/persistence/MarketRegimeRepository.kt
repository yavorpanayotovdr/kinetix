package com.kinetix.risk.persistence

import com.kinetix.risk.model.MarketRegimeHistory
import com.kinetix.risk.model.RegimeState
import java.time.Instant
import java.util.UUID

interface MarketRegimeRepository {
    /**
     * Insert a new regime history record.
     * Called when a new regime starts (open-ended — endedAt is null).
     */
    suspend fun insert(state: RegimeState, id: UUID = UUID.randomUUID()): UUID

    /**
     * Close the previous regime record when a transition occurs.
     * Sets endedAt and durationMs on the record with the given id.
     */
    suspend fun close(id: UUID, endedAt: Instant)

    /** Returns the most recent open regime record (endedAt IS NULL). */
    suspend fun findCurrent(): MarketRegimeHistory?

    /** Returns regime history in descending order of startedAt, limited to [limit] rows. */
    suspend fun findRecent(limit: Int = 100): List<MarketRegimeHistory>
}
