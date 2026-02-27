package com.kinetix.gateway.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import java.time.Instant
import io.ktor.http.*

class HttpRiskServiceClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) : RiskServiceClient {

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
        val dto: ValuationResultDto = response.body()
        return dto.toDomain()
    }

    override suspend fun getLatestVaR(portfolioId: String): ValuationResultSummary? {
        val response = httpClient.get("$baseUrl/api/v1/risk/var/$portfolioId")
        if (response.status == HttpStatusCode.NotFound) return null
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
        val dto: StressTestResultDto = response.body()
        return dto.toDomain()
    }

    override suspend fun listScenarios(): List<String> {
        val response = httpClient.get("$baseUrl/api/v1/risk/stress/scenarios")
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
        val dto: FrtbResultDto = response.body()
        return dto.toDomain()
    }

    override suspend fun generateReport(portfolioId: String, format: String): ReportResult? {
        val response = httpClient.post("$baseUrl/api/v1/regulatory/report/$portfolioId") {
            contentType(ContentType.Application.Json)
            setBody(GenerateReportRequestDto(format = format))
        }
        if (response.status == HttpStatusCode.NotFound) return null
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
        val dto: DataDependenciesDto = response.body()
        return dto.toDomain()
    }

    override suspend fun listValuationJobs(portfolioId: String, limit: Int, offset: Int, from: Instant?, to: Instant?): Pair<List<ValuationJobSummaryItem>, Long> {
        val response = httpClient.get("$baseUrl/api/v1/risk/jobs/$portfolioId") {
            url {
                parameters.append("limit", limit.toString())
                parameters.append("offset", offset.toString())
                if (from != null) parameters.append("from", from.toString())
                if (to != null) parameters.append("to", to.toString())
            }
        }
        val dto: PaginatedJobsClientDto = response.body()
        return Pair(dto.items.map { it.toDomain() }, dto.totalCount)
    }

    override suspend fun getValuationJobDetail(jobId: String): ValuationJobDetailItem? {
        val response = httpClient.get("$baseUrl/api/v1/risk/jobs/detail/$jobId")
        if (response.status == HttpStatusCode.NotFound) return null
        val dto: ValuationJobDetailClientDto = response.body()
        return dto.toDomain()
    }
}
