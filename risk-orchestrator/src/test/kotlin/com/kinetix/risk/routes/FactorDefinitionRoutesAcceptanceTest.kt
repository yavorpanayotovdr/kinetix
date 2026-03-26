package com.kinetix.risk.routes

import com.kinetix.risk.model.FactorDefinition
import com.kinetix.risk.persistence.FactorDefinitionRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class FactorDefinitionRoutesAcceptanceTest : FunSpec({

    val repository = mockk<FactorDefinitionRepository>()

    beforeEach {
        clearMocks(repository)
    }

    fun testApp(block: suspend ApplicationTestBuilder.() -> Unit) {
        testApplication {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing {
                factorDefinitionRoutes(repository)
            }
            block()
        }
    }

    val seedDefinitions = listOf(
        FactorDefinition("EQUITY_BETA", "IDX-SPX", "Equity market beta — proxy: S&P 500"),
        FactorDefinition("RATES_DURATION", "US10Y", "Rates duration — proxy: 10-year UST yield"),
        FactorDefinition("CREDIT_SPREAD", "CDX-IG", "Credit spread — proxy: IG credit index"),
        FactorDefinition("FX_DELTA", "EURUSD", "FX delta — proxy: EUR/USD"),
        FactorDefinition("VOL_EXPOSURE", "VIX", "Volatility exposure — proxy: VIX"),
    )

    test("GET /api/v1/factor-definitions returns all factor definitions") {
        coEvery { repository.findAll() } returns seedDefinitions

        testApp {
            val response = client.get("/api/v1/factor-definitions")

            response.status shouldBe HttpStatusCode.OK

            val arr = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            arr.size shouldBe 5
        }
    }

    test("GET /api/v1/factor-definitions returns correct fields for each definition") {
        coEvery { repository.findAll() } returns seedDefinitions

        testApp {
            val response = client.get("/api/v1/factor-definitions")

            val arr = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            val equity = arr.first { it.jsonObject["factorName"]?.jsonPrimitive?.content == "EQUITY_BETA" }.jsonObject

            equity["proxyInstrumentId"]?.jsonPrimitive?.content shouldBe "IDX-SPX"
            equity["description"]?.jsonPrimitive?.content shouldBe "Equity market beta — proxy: S&P 500"
        }
    }

    test("GET /api/v1/factor-definitions returns empty array when no definitions exist") {
        coEvery { repository.findAll() } returns emptyList()

        testApp {
            val response = client.get("/api/v1/factor-definitions")

            response.status shouldBe HttpStatusCode.OK
            Json.parseToJsonElement(response.bodyAsText()).jsonArray.size shouldBe 0
        }
    }

    test("GET /api/v1/factor-definitions/{name} returns a single definition") {
        coEvery { repository.findByName("EQUITY_BETA") } returns seedDefinitions.first()

        testApp {
            val response = client.get("/api/v1/factor-definitions/EQUITY_BETA")

            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["factorName"]?.jsonPrimitive?.content shouldBe "EQUITY_BETA"
            body["proxyInstrumentId"]?.jsonPrimitive?.content shouldBe "IDX-SPX"
        }
    }

    test("GET /api/v1/factor-definitions/{name} returns 404 for unknown factor") {
        coEvery { repository.findByName("UNKNOWN") } returns null

        testApp {
            val response = client.get("/api/v1/factor-definitions/UNKNOWN")

            response.status shouldBe HttpStatusCode.NotFound
        }
    }
})
