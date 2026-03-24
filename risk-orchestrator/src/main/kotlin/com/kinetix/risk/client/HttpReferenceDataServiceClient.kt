package com.kinetix.risk.client

import com.kinetix.common.model.CreditSpread
import com.kinetix.common.model.DividendYield
import com.kinetix.common.model.InstrumentId
import com.kinetix.risk.client.dtos.CreditSpreadDto
import com.kinetix.risk.client.dtos.DividendYieldDto
import com.kinetix.risk.client.dtos.InstrumentLiquidityDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

class HttpReferenceDataServiceClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) : ReferenceDataServiceClient {

    override suspend fun getLatestDividendYield(instrumentId: InstrumentId): ClientResponse<DividendYield> {
        val response = httpClient.get("$baseUrl/api/v1/reference-data/dividends/${instrumentId.value}/latest")
        if (response.status == HttpStatusCode.NotFound) return ClientResponse.NotFound(response.status.value)
        val dto: DividendYieldDto = response.body()
        return ClientResponse.Success(dto.toDomain())
    }

    override suspend fun getLatestCreditSpread(instrumentId: InstrumentId): ClientResponse<CreditSpread> {
        val response = httpClient.get("$baseUrl/api/v1/reference-data/credit-spreads/${instrumentId.value}/latest")
        if (response.status == HttpStatusCode.NotFound) return ClientResponse.NotFound(response.status.value)
        val dto: CreditSpreadDto = response.body()
        return ClientResponse.Success(dto.toDomain())
    }

    override suspend fun getLiquidityData(instrumentId: String): ClientResponse<InstrumentLiquidityDto> {
        val response = httpClient.get("$baseUrl/api/v1/liquidity/$instrumentId")
        if (response.status == HttpStatusCode.NotFound) return ClientResponse.NotFound(response.status.value)
        val dto: InstrumentLiquidityDto = response.body()
        return ClientResponse.Success(dto)
    }

    override suspend fun getLiquidityDataBatch(instrumentIds: List<String>): Map<String, InstrumentLiquidityDto> {
        if (instrumentIds.isEmpty()) return emptyMap()
        val ids = instrumentIds.joinToString(",")
        val response = httpClient.get("$baseUrl/api/v1/liquidity/batch?ids=$ids")
        if (!response.status.value.toString().startsWith("2")) return emptyMap()
        val dtos: List<InstrumentLiquidityDto> = response.body()
        return dtos.associateBy { it.instrumentId }
    }
}
