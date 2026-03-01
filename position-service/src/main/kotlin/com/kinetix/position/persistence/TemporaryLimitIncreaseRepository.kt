package com.kinetix.position.persistence

import com.kinetix.position.model.TemporaryLimitIncrease
import java.time.Instant

interface TemporaryLimitIncreaseRepository {
    suspend fun findActiveByLimitId(limitId: String, asOf: Instant): TemporaryLimitIncrease?
    suspend fun save(temporaryLimitIncrease: TemporaryLimitIncrease)
}
