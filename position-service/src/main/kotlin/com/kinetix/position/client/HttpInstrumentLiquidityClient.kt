package com.kinetix.position.client

import com.kinetix.common.model.InstrumentId
import com.kinetix.position.client.dtos.InstrumentLiquidityDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import java.math.BigDecimal

class HttpInstrumentLiquidityClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) : InstrumentLiquidityClient {

    override suspend fun getAdv(instrumentId: InstrumentId): BigDecimal? {
        val response = httpClient.get("$baseUrl/api/v1/liquidity/${instrumentId.value}")
        if (response.status == HttpStatusCode.NotFound) return null
        return response.body<InstrumentLiquidityDto>().adv.toBigDecimal()
    }
}
