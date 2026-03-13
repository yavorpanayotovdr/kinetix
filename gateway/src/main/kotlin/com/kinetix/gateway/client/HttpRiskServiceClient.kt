package com.kinetix.gateway.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

class HttpRiskServiceClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) : RiskServiceClient {

    @Serializable
    private data class UpstreamErrorBody(val code: String = "", val message: String = "")

    private val lenientJson = Json { ignoreUnknownKeys = true }

    private suspend fun handleErrorResponse(response: HttpResponse): Nothing {
        val body = try {
            response.bodyAsText()
        } catch (_: Exception) {
            ""
        }

        val message = try {
            lenientJson.decodeFromString<UpstreamErrorBody>(body).message
        } catch (_: Exception) {
            body.ifBlank { response.status.description }
        }

        when (response.status.value) {
            503 -> {
                val retryAfter = response.headers[HttpHeaders.RetryAfter]?.toIntOrNull()
                throw ServiceUnavailableException(retryAfter, message)
            }
            504 -> throw GatewayTimeoutException(message)
            else -> throw UpstreamErrorException(response.status.value, message)
        }
    }

    override suspend fun getMarginEstimate(portfolioId: String, previousMTM: String?): MarginEstimateSummary? {
        val response = httpClient.get("$baseUrl/api/v1/portfolios/$portfolioId/margin") {
            if (previousMTM != null) {
                url { parameters.append("previousMTM", previousMTM) }
            }
        }
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) handleErrorResponse(response)
        return response.body<MarginEstimateClientDto>().toDomain()
    }

    override suspend fun calculateVaR(params: VaRCalculationParams): ValuationResultSummary? {
        val response = httpClient.post("$baseUrl/api/v1/risk/var/${params.portfolioId}") {
            contentType(ContentType.Application.Json)
            setBody(
                VaRCalculationRequestDto(
                    calculationType = params.calculationType,
                    confidenceLevel = params.confidenceLevel,
                    timeHorizonDays = params.timeHorizonDays.toString(),
                    numSimulations = params.numSimulations.toString(),
                    requestedOutputs = params.requestedOutputs,
                )
            )
        }
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) handleErrorResponse(response)
        val dto: ValuationResultDto = response.body()
        return dto.toDomain()
    }

    override suspend fun getLatestVaR(portfolioId: String, valuationDate: String?): ValuationResultSummary? {
        val response = httpClient.get("$baseUrl/api/v1/risk/var/$portfolioId") {
            if (!valuationDate.isNullOrBlank()) {
                url { parameters.append("valuationDate", valuationDate) }
            }
        }
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) handleErrorResponse(response)
        val dto: ValuationResultDto = response.body()
        return dto.toDomain()
    }

    override suspend fun runStressTest(params: StressTestParams): StressTestResultSummary? {
        val response = httpClient.post("$baseUrl/api/v1/risk/stress/${params.portfolioId}") {
            contentType(ContentType.Application.Json)
            setBody(
                StressTestRequestDto(
                    scenarioName = params.scenarioName,
                    calculationType = params.calculationType,
                    confidenceLevel = params.confidenceLevel,
                    timeHorizonDays = params.timeHorizonDays.toString(),
                    volShocks = params.volShocks,
                    priceShocks = params.priceShocks,
                    description = params.description,
                )
            )
        }
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) handleErrorResponse(response)
        val dto: StressTestResultDto = response.body()
        return dto.toDomain()
    }

    override suspend fun runBatchStressTest(params: StressTestBatchParams): List<StressTestResultSummary> {
        val response = httpClient.post("$baseUrl/api/v1/risk/stress/${params.portfolioId}/batch") {
            contentType(ContentType.Application.Json)
            setBody(
                StressTestBatchRequestDto(
                    scenarioNames = params.scenarioNames,
                    calculationType = params.calculationType,
                    confidenceLevel = params.confidenceLevel,
                    timeHorizonDays = params.timeHorizonDays.toString(),
                )
            )
        }
        if (!response.status.isSuccess()) handleErrorResponse(response)
        val dtos: List<StressTestResultDto> = response.body()
        return dtos.map { it.toDomain() }
    }

    override suspend fun listScenarios(): List<String> {
        val response = httpClient.get("$baseUrl/api/v1/risk/stress/scenarios")
        if (!response.status.isSuccess()) handleErrorResponse(response)
        return response.body()
    }

    override suspend fun calculateGreeks(params: VaRCalculationParams): GreeksResultSummary? {
        val result = calculateVaR(
            params.copy(requestedOutputs = listOf("VAR", "EXPECTED_SHORTFALL", "GREEKS"))
        )
        return result?.greeks
    }

    override suspend fun calculateFrtb(portfolioId: String): FrtbResultSummary? {
        val response = httpClient.post("$baseUrl/api/v1/regulatory/frtb/$portfolioId") {
            contentType(ContentType.Application.Json)
        }
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) handleErrorResponse(response)
        val dto: FrtbResultDto = response.body()
        return dto.toDomain()
    }

    override suspend fun generateReport(portfolioId: String, format: String): ReportResult? {
        val response = httpClient.post("$baseUrl/api/v1/regulatory/report/$portfolioId") {
            contentType(ContentType.Application.Json)
            setBody(GenerateReportRequestDto(format = format))
        }
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) handleErrorResponse(response)
        val dto: ReportResultDto = response.body()
        return dto.toDomain()
    }

    override suspend fun discoverDependencies(params: DependenciesParams): DataDependenciesSummary? {
        val response = httpClient.post("$baseUrl/api/v1/risk/dependencies/${params.portfolioId}") {
            contentType(ContentType.Application.Json)
            setBody(
                DependenciesRequestDto(
                    calculationType = params.calculationType,
                    confidenceLevel = params.confidenceLevel,
                )
            )
        }
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) handleErrorResponse(response)
        val dto: DataDependenciesDto = response.body()
        return dto.toDomain()
    }

    override suspend fun listValuationJobs(portfolioId: String, limit: Int, offset: Int, from: Instant?, to: Instant?, valuationDate: String?): Pair<List<ValuationJobSummaryItem>, Long> {
        val response = httpClient.get("$baseUrl/api/v1/risk/jobs/$portfolioId") {
            url {
                parameters.append("limit", limit.toString())
                parameters.append("offset", offset.toString())
                if (from != null) parameters.append("from", from.toString())
                if (to != null) parameters.append("to", to.toString())
                if (!valuationDate.isNullOrBlank()) parameters.append("valuationDate", valuationDate)
            }
        }
        if (!response.status.isSuccess()) handleErrorResponse(response)
        val dto: PaginatedJobsClientDto = response.body()
        return Pair(dto.items.map { it.toDomain() }, dto.totalCount)
    }

    override suspend fun getValuationJobDetail(jobId: String): ValuationJobDetailItem? {
        val response = httpClient.get("$baseUrl/api/v1/risk/jobs/detail/$jobId")
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) handleErrorResponse(response)
        val dto: ValuationJobDetailClientDto = response.body()
        return dto.toDomain()
    }

    override suspend fun getSodBaselineStatus(portfolioId: String): SodBaselineStatusSummary? {
        val response = httpClient.get("$baseUrl/api/v1/risk/sod-snapshot/$portfolioId/status")
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) handleErrorResponse(response)
        val dto: SodBaselineStatusClientDto = response.body()
        return dto.toDomain()
    }

    override suspend fun createSodSnapshot(portfolioId: String, jobId: String?): SodBaselineStatusSummary {
        val response = httpClient.post("$baseUrl/api/v1/risk/sod-snapshot/$portfolioId") {
            contentType(ContentType.Application.Json)
            if (jobId != null) {
                url.parameters.append("jobId", jobId)
            }
        }
        if (!response.status.isSuccess()) handleErrorResponse(response)
        val dto: SodBaselineStatusClientDto = response.body()
        return dto.toDomain()
    }

    override suspend fun resetSodBaseline(portfolioId: String) {
        httpClient.delete("$baseUrl/api/v1/risk/sod-snapshot/$portfolioId")
    }

    override suspend fun computePnlAttribution(portfolioId: String): PnlAttributionSummary {
        val response = httpClient.post("$baseUrl/api/v1/risk/pnl-attribution/$portfolioId/compute") {
            contentType(ContentType.Application.Json)
        }
        if (!response.status.isSuccess()) handleErrorResponse(response)
        val dto: PnlAttributionClientDto = response.body()
        return dto.toDomain()
    }

    override suspend fun runWhatIf(params: WhatIfRequestParams): WhatIfResultSummary {
        val response = httpClient.post("$baseUrl/api/v1/risk/what-if/${params.portfolioId}") {
            contentType(ContentType.Application.Json)
            setBody(
                WhatIfRequestClientDto(
                    hypotheticalTrades = params.hypotheticalTrades.map {
                        HypotheticalTradeClientDto(
                            instrumentId = it.instrumentId,
                            assetClass = it.assetClass,
                            side = it.side,
                            quantity = it.quantity,
                            priceAmount = it.priceAmount,
                            priceCurrency = it.priceCurrency,
                        )
                    },
                    calculationType = params.calculationType,
                    confidenceLevel = params.confidenceLevel,
                )
            )
        }
        if (!response.status.isSuccess()) handleErrorResponse(response)
        val dto: WhatIfResultClientDto = response.body()
        return dto.toDomain()
    }

    override suspend fun getPositionRisk(portfolioId: String, valuationDate: String?): List<PositionRiskSummaryItem>? {
        val response = httpClient.get("$baseUrl/api/v1/risk/positions/$portfolioId") {
            if (!valuationDate.isNullOrBlank()) {
                url { parameters.append("valuationDate", valuationDate) }
            }
        }
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) handleErrorResponse(response)
        val dtos: List<PositionRiskClientDto> = response.body()
        return dtos.map { it.toDomain() }
    }

    override suspend fun getPnlAttribution(portfolioId: String, date: String?): PnlAttributionSummary? {
        val response = httpClient.get("$baseUrl/api/v1/risk/pnl-attribution/$portfolioId") {
            if (date != null) {
                url { parameters.append("date", date) }
            }
        }
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) handleErrorResponse(response)
        val dto: PnlAttributionClientDto = response.body()
        return dto.toDomain()
    }

    override suspend fun compareRuns(portfolioId: String, baseJobId: String, targetJobId: String): JsonObject {
        val response = httpClient.post("$baseUrl/api/v1/risk/compare/$portfolioId") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("baseJobId", baseJobId)
                put("targetJobId", targetJobId)
            })
        }
        if (!response.status.isSuccess()) handleErrorResponse(response)
        return response.body()
    }

    override suspend fun compareDayOverDay(portfolioId: String, targetDate: String?, baseDate: String?): JsonObject? {
        val response = httpClient.get("$baseUrl/api/v1/risk/compare/$portfolioId/day-over-day") {
            url {
                if (targetDate != null) parameters.append("targetDate", targetDate)
                if (baseDate != null) parameters.append("baseDate", baseDate)
            }
        }
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) handleErrorResponse(response)
        return response.body()
    }

    override suspend fun compareDayOverDayAttribution(portfolioId: String, targetDate: String?, baseDate: String?): JsonObject {
        val response = httpClient.post("$baseUrl/api/v1/risk/compare/$portfolioId/day-over-day/attribution") {
            contentType(ContentType.Application.Json)
            url {
                if (targetDate != null) parameters.append("targetDate", targetDate)
                if (baseDate != null) parameters.append("baseDate", baseDate)
            }
        }
        if (!response.status.isSuccess()) handleErrorResponse(response)
        return response.body()
    }

    override suspend fun compareModel(portfolioId: String, request: JsonObject): JsonObject {
        val response = httpClient.post("$baseUrl/api/v1/risk/compare/$portfolioId/model") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) handleErrorResponse(response)
        return response.body()
    }
}
