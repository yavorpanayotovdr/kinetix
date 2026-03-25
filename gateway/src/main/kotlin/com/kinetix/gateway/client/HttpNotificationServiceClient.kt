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

    override suspend fun listAlerts(limit: Int, status: String?): List<AlertEventItem> {
        val response = httpClient.get("$baseUrl/api/v1/notifications/alerts") {
            parameter("limit", limit)
            if (status != null) parameter("status", status)
        }
        val dtos: List<AlertEventDto> = response.body()
        return dtos.map { it.toDomain() }
    }

    override suspend fun listEscalatedAlerts(): List<AlertEventItem> {
        val response = httpClient.get("$baseUrl/api/v1/notifications/alerts/escalated")
        val dtos: List<AlertEventDto> = response.body()
        return dtos.map { it.toDomain() }
    }

    override suspend fun getAlertContributors(alertId: String): String? {
        val response = httpClient.get("$baseUrl/api/v1/notifications/alerts/$alertId/contributors")
        if (response.status == HttpStatusCode.NotFound) return null
        return response.body<String>()
    }

    override suspend fun acknowledgeAlert(alertId: String, params: AcknowledgeAlertParams): AlertEventItem? {
        val response = httpClient.post("$baseUrl/api/v1/notifications/alerts/$alertId/acknowledge") {
            contentType(ContentType.Application.Json)
            setBody(
                AcknowledgeAlertRequestDto(
                    acknowledgedBy = params.acknowledgedBy,
                    notes = params.notes,
                ),
            )
        }
        if (response.status == HttpStatusCode.NotFound || response.status == HttpStatusCode.Conflict) {
            return null
        }
        val dto: AlertEventDto = response.body()
        return dto.toDomain()
    }
}
