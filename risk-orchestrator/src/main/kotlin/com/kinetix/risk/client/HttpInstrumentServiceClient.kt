package com.kinetix.risk.client

import com.kinetix.common.model.InstrumentId
import com.kinetix.risk.client.dtos.InstrumentDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import java.util.concurrent.ConcurrentHashMap

class HttpInstrumentServiceClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) : InstrumentServiceClient {

    private val cache = ConcurrentHashMap<String, InstrumentDto>()

    override suspend fun getInstrument(instrumentId: InstrumentId): ClientResponse<InstrumentDto> {
        cache[instrumentId.value]?.let { return ClientResponse.Success(it) }
        val response = httpClient.get("$baseUrl/api/v1/instruments/${instrumentId.value}")
        if (response.status == HttpStatusCode.NotFound) return ClientResponse.NotFound(response.status.value)
        val dto: InstrumentDto = response.body()
        cache[instrumentId.value] = dto
        return ClientResponse.Success(dto)
    }

    override suspend fun getInstruments(instrumentIds: List<InstrumentId>): Map<String, InstrumentDto> {
        val result = mutableMapOf<String, InstrumentDto>()
        for (id in instrumentIds) {
            when (val resp = getInstrument(id)) {
                is ClientResponse.Success -> result[id.value] = resp.value
                else -> {}
            }
        }
        return result
    }
}
