package com.kinetix.risk.client

import com.kinetix.common.model.CreditSpread
import com.kinetix.common.model.DividendYield
import com.kinetix.common.model.InstrumentId
import com.kinetix.risk.client.dtos.CreditSpreadDto
import com.kinetix.risk.client.dtos.DividendYieldDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

class HttpReferenceDataServiceClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) : ReferenceDataServiceClient {

    override suspend fun getLatestDividendYield(instrumentId: InstrumentId): DividendYield? {
        val response = httpClient.get("$baseUrl/api/v1/reference-data/dividends/${instrumentId.value}/latest")
        if (response.status == HttpStatusCode.NotFound) return null
        val dto: DividendYieldDto = response.body()
        return dto.toDomain()
    }

    override suspend fun getLatestCreditSpread(instrumentId: InstrumentId): CreditSpread? {
        val response = httpClient.get("$baseUrl/api/v1/reference-data/credit-spreads/${instrumentId.value}/latest")
        if (response.status == HttpStatusCode.NotFound) return null
        val dto: CreditSpreadDto = response.body()
        return dto.toDomain()
    }
}
