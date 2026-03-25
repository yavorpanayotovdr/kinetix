package com.kinetix.gateway.client

import com.kinetix.gateway.dto.VolSurfaceDiffResponse
import com.kinetix.gateway.dto.VolSurfaceResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode

class HttpVolatilityServiceClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) : VolatilityServiceClient {

    override suspend fun getSurface(instrumentId: String): VolSurfaceResponse? {
        val response = httpClient.get("$baseUrl/api/v1/volatility/$instrumentId/surface/latest")
        if (response.status == HttpStatusCode.NotFound) return null
        return response.body()
    }

    override suspend fun getSurfaceDiff(instrumentId: String, compareDate: String): VolSurfaceDiffResponse? {
        val response = httpClient.get("$baseUrl/api/v1/volatility/$instrumentId/surface/diff") {
            parameter("compareDate", compareDate)
        }
        if (response.status == HttpStatusCode.NotFound) return null
        return response.body()
    }
}
