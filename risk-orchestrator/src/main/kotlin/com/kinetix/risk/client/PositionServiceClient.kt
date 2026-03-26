package com.kinetix.risk.client

import com.kinetix.common.model.BookId
import com.kinetix.common.model.Position
import com.kinetix.risk.client.dtos.CounterpartyTradeDto
import com.kinetix.risk.client.dtos.NetCollateralDto
import com.kinetix.risk.client.dtos.TradeDto
import java.time.Instant

interface PositionServiceClient {
    suspend fun getPositions(bookId: BookId): ClientResponse<List<Position>>
    suspend fun getDistinctBookIds(): ClientResponse<List<BookId>>
    suspend fun getTradesInRange(bookId: BookId, from: Instant, to: Instant): ClientResponse<List<TradeDto>>
    suspend fun getNetCollateral(counterpartyId: String): ClientResponse<NetCollateralDto>

    /**
     * Returns a map of instrumentId -> nettingSetId for all live trades booked
     * against the given counterparty.  Instruments with no netting-set assignment
     * are absent from the map.
     */
    suspend fun getInstrumentNettingSets(counterpartyId: String): ClientResponse<Map<String, String>>

    /** Returns all trades for a counterparty (for SA-CCR position assembly). */
    suspend fun getTradesByCounterparty(counterpartyId: String): ClientResponse<List<CounterpartyTradeDto>>
}
