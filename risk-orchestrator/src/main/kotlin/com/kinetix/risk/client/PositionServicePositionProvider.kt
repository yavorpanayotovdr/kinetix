package com.kinetix.risk.client

import com.kinetix.common.model.BookId
import com.kinetix.common.model.Position
import org.slf4j.LoggerFactory

class PositionServicePositionProvider(
    private val positionServiceClient: PositionServiceClient,
) : PositionProvider {

    private val logger = LoggerFactory.getLogger(PositionServicePositionProvider::class.java)

    override suspend fun getPositions(bookId: BookId): List<Position> {
        return when (val response = positionServiceClient.getPositions(bookId)) {
            is ClientResponse.Success -> response.value
            is ClientResponse.NotFound -> emptyList()
            is ClientResponse.ServiceUnavailable -> {
                logger.warn("Position service unavailable for book {}, returning empty positions", bookId.value)
                emptyList()
            }
            is ClientResponse.UpstreamError -> {
                logger.warn(
                    "Position service returned error {} for book {}: {}, returning empty positions",
                    response.httpStatus, bookId.value, response.message,
                )
                emptyList()
            }
            is ClientResponse.NetworkError -> {
                logger.warn("Network error fetching positions for book {}, returning empty positions", bookId.value, response.cause)
                emptyList()
            }
        }
    }
}
