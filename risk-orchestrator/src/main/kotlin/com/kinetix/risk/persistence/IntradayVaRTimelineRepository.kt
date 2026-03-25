package com.kinetix.risk.persistence

import com.kinetix.common.model.BookId
import com.kinetix.risk.model.IntradayVaRPoint
import java.time.Instant

interface IntradayVaRTimelineRepository {
    suspend fun findInRange(bookId: BookId, from: Instant, to: Instant): List<IntradayVaRPoint>
}
