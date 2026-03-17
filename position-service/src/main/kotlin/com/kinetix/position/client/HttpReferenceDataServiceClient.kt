package com.kinetix.position.client

import com.kinetix.common.model.Desk
import com.kinetix.common.model.DeskId
import com.kinetix.common.model.Division
import com.kinetix.common.model.DivisionId
import com.kinetix.position.client.dtos.DeskDto
import com.kinetix.position.client.dtos.DivisionDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

class HttpReferenceDataServiceClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) : ReferenceDataServiceClient {

    override suspend fun getDeskById(deskId: DeskId): Desk? {
        val response = httpClient.get("$baseUrl/api/v1/desks/${deskId.value}")
        if (response.status == HttpStatusCode.NotFound) return null
        return response.body<DeskDto>().toDomain()
    }

    override suspend fun getDivisionById(divisionId: DivisionId): Division? {
        val response = httpClient.get("$baseUrl/api/v1/divisions/${divisionId.value}")
        if (response.status == HttpStatusCode.NotFound) return null
        return response.body<DivisionDto>().toDomain()
    }
}
