package com.kinetix.risk.service

import com.kinetix.common.model.BookId
import com.kinetix.risk.client.ClientResponse
import com.kinetix.risk.client.PositionServiceClient
import com.kinetix.risk.model.TradeAnnotation
import java.time.Instant

class PositionServiceTradeProvider(
    private val positionServiceClient: PositionServiceClient,
) : IntradayVaRTradeProvider {

    override suspend fun getTradesInRange(
        bookId: BookId,
        from: Instant,
        to: Instant,
    ): List<TradeAnnotation> {
        return when (val response = positionServiceClient.getTradesInRange(bookId, from, to)) {
            is ClientResponse.Success -> response.value.map { it.toTradeAnnotation() }
            is ClientResponse.NotFound -> emptyList()
        }
    }
}
