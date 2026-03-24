package com.kinetix.risk.persistence

import com.kinetix.risk.model.HedgeRecommendation
import java.util.UUID

interface HedgeRecommendationRepository {
    suspend fun save(recommendation: HedgeRecommendation)
    suspend fun findById(id: UUID): HedgeRecommendation?
    suspend fun findLatestByBookId(bookId: String, limit: Int = 10): List<HedgeRecommendation>
    suspend fun updateStatus(id: UUID, status: com.kinetix.risk.model.HedgeStatus, acceptedBy: String? = null, acceptedAt: java.time.Instant? = null)
    suspend fun expirePending(): Int
}
