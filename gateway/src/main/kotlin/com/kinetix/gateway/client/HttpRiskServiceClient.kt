package com.kinetix.gateway.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class HttpRiskServiceClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) : RiskServiceClient {

    override suspend fun calculateVaR(params: VaRCalculationParams): VaRResultSummary? {
        val response = httpClient.post("$baseUrl/api/v1/risk/var/${params.portfolioId}") {
            contentType(ContentType.Application.Json)
            setBody(
                VaRCalculationRequestDto(
                    calculationType = params.calculationType,
                    confidenceLevel = params.confidenceLevel,
                    timeHorizonDays = params.timeHorizonDays.toString(),
                    numSimulations = params.numSimulations.toString(),
                )
            )
        }
        if (response.status == HttpStatusCode.NotFound) return null
        val dto: VaRResultDto = response.body()
        return dto.toDomain()
    }

    override suspend fun getLatestVaR(portfolioId: String): VaRResultSummary? {
        val response = httpClient.get("$baseUrl/api/v1/risk/var/$portfolioId")
        if (response.status == HttpStatusCode.NotFound) return null
        val dto: VaRResultDto = response.body()
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
        val response = httpClient.post("$baseUrl/api/v1/risk/greeks/${params.portfolioId}") {
            contentType(ContentType.Application.Json)
            setBody(
                VaRCalculationRequestDto(
                    calculationType = params.calculationType,
                    confidenceLevel = params.confidenceLevel,
                    timeHorizonDays = params.timeHorizonDays.toString(),
                    numSimulations = params.numSimulations.toString(),
                )
            )
        }
        if (response.status == HttpStatusCode.NotFound) return null
        val dto: GreeksResultDto = response.body()
        return dto.toDomain()
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
}
