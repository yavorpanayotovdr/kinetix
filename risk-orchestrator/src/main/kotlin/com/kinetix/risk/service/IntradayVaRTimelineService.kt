package com.kinetix.risk.service

import com.kinetix.common.model.BookId
import com.kinetix.risk.model.IntradayVaRTimelineResult
import com.kinetix.risk.persistence.IntradayVaRTimelineRepository
import java.time.Instant

class IntradayVaRTimelineService(
    private val repository: IntradayVaRTimelineRepository,
    private val tradeProvider: IntradayVaRTradeProvider,
) {
    suspend fun getTimeline(bookId: BookId, from: Instant, to: Instant): IntradayVaRTimelineResult {
        val varPoints = repository.findInRange(bookId, from, to)
            .sortedBy { it.timestamp }

        val tradeAnnotations = tradeProvider.getTradesInRange(bookId, from, to)
            .sortedBy { it.timestamp }

        return IntradayVaRTimelineResult(
            varPoints = varPoints,
            tradeAnnotations = tradeAnnotations,
        )
    }
}
