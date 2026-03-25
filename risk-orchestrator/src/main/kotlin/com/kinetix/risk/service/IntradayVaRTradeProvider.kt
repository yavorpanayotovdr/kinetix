package com.kinetix.risk.service

import com.kinetix.common.model.BookId
import com.kinetix.risk.model.TradeAnnotation
import java.time.Instant

interface IntradayVaRTradeProvider {
    suspend fun getTradesInRange(bookId: BookId, from: Instant, to: Instant): List<TradeAnnotation>
}
