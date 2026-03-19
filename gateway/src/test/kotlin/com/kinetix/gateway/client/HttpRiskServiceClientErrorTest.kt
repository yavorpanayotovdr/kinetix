package com.kinetix.gateway.client

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class HttpRiskServiceClientErrorTest : FunSpec({

    fun buildClient(mockEngine: MockEngine): HttpRiskServiceClient {
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return HttpRiskServiceClient(httpClient, "http://localhost")
    }

    fun defaultVaRParams() = VaRCalculationParams(
        bookId = "port-1",
        calculationType = "HISTORICAL",
        confidenceLevel = "0.99",
        timeHorizonDays = 10,
        numSimulations = 1000,
        requestedOutputs = listOf("VAR"),
    )

    test("throws ServiceUnavailableException on 503 from risk-orchestrator") {
        val mockEngine = MockEngine { _ ->
            respond(
                content = """{"code":"service_unavailable","message":"Risk engine overloaded"}""",
                status = HttpStatusCode.ServiceUnavailable,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    HttpHeaders.RetryAfter to listOf("30"),
                ),
            )
        }
        val sut = buildClient(mockEngine)

        val ex = shouldThrow<ServiceUnavailableException> {
            sut.calculateVaR(defaultVaRParams())
        }
        ex.message shouldContain "Risk engine overloaded"
        ex.retryAfterSeconds shouldBe 30
    }

    test("throws GatewayTimeoutException on 504 from risk-orchestrator") {
        val mockEngine = MockEngine { _ ->
            respond(
                content = """{"code":"gateway_timeout","message":"Upstream timed out"}""",
                status = HttpStatusCode.GatewayTimeout,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val sut = buildClient(mockEngine)

        val ex = shouldThrow<GatewayTimeoutException> {
            sut.calculateVaR(defaultVaRParams())
        }
        ex.message shouldContain "Upstream timed out"
    }

    test("throws UpstreamErrorException with parsed message on 500") {
        val mockEngine = MockEngine { _ ->
            respond(
                content = """{"code":"internal_error","message":"numpy failed"}""",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val sut = buildClient(mockEngine)

        val ex = shouldThrow<UpstreamErrorException> {
            sut.calculateVaR(defaultVaRParams())
        }
        ex.statusCode shouldBe 500
        ex.message shouldContain "numpy failed"
    }
})
