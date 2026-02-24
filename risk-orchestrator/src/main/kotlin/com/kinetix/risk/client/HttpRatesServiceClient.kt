package com.kinetix.risk.client

import com.kinetix.common.model.*
import com.kinetix.risk.client.dtos.ForwardCurveDto
import com.kinetix.risk.client.dtos.RiskFreeRateDto
import com.kinetix.risk.client.dtos.YieldCurveDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode

class HttpRatesServiceClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) : RatesServiceClient {

    override suspend fun getLatestYieldCurve(curveId: String): YieldCurve? {
        val response = httpClient.get("$baseUrl/api/v1/rates/yield-curves/$curveId/latest")
        if (response.status == HttpStatusCode.NotFound) return null
        val dto: YieldCurveDto = response.body()
        return dto.toDomain()
    }

    override suspend fun getLatestRiskFreeRate(currency: java.util.Currency, tenor: String): RiskFreeRate? {
        val response = httpClient.get("$baseUrl/api/v1/rates/risk-free/${currency.currencyCode}/latest") {
            parameter("tenor", tenor)
        }
        if (response.status == HttpStatusCode.NotFound) return null
        val dto: RiskFreeRateDto = response.body()
        return dto.toDomain()
    }

    override suspend fun getLatestForwardCurve(instrumentId: InstrumentId): ForwardCurve? {
        val response = httpClient.get("$baseUrl/api/v1/rates/forwards/${instrumentId.value}/latest")
        if (response.status == HttpStatusCode.NotFound) return null
        val dto: ForwardCurveDto = response.body()
        return dto.toDomain()
    }
}
