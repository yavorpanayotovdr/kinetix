package com.kinetix.gateway.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class HttpNotificationServiceClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) : NotificationServiceClient {

    override suspend fun listRules(): List<AlertRuleItem> {
        val response = httpClient.get("$baseUrl/api/v1/notifications/rules")
        val dtos: List<AlertRuleDto> = response.body()
        return dtos.map { it.toDomain() }
    }

    override suspend fun createRule(params: CreateAlertRuleParams): AlertRuleItem {
        val response = httpClient.post("$baseUrl/api/v1/notifications/rules") {
            contentType(ContentType.Application.Json)
            setBody(
                CreateAlertRuleRequestDto(
                    name = params.name,
                    type = params.type,
                    threshold = params.threshold,
                    operator = params.operator,
                    severity = params.severity,
                    channels = params.channels,
                )
            )
        }
        val dto: AlertRuleDto = response.body()
        return dto.toDomain()
    }

    override suspend fun deleteRule(ruleId: String): Boolean {
        val response = httpClient.delete("$baseUrl/api/v1/notifications/rules/$ruleId")
        return response.status == HttpStatusCode.NoContent
    }

    override suspend fun listAlerts(limit: Int): List<AlertEventItem> {
        val response = httpClient.get("$baseUrl/api/v1/notifications/alerts") {
            parameter("limit", limit)
        }
        val dtos: List<AlertEventDto> = response.body()
        return dtos.map { it.toDomain() }
    }
}
