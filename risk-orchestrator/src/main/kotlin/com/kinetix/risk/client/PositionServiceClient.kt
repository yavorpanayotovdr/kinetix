package com.kinetix.risk.client

import com.kinetix.common.model.BookId
import com.kinetix.common.model.Position
import com.kinetix.risk.client.dtos.TradeDto
import java.time.Instant

interface PositionServiceClient {
    suspend fun getPositions(bookId: BookId): ClientResponse<List<Position>>
    suspend fun getDistinctBookIds(): ClientResponse<List<BookId>>
    suspend fun getTradesInRange(bookId: BookId, from: Instant, to: Instant): ClientResponse<List<TradeDto>>
}
