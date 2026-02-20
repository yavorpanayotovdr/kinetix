package com.kinetix.audit.routes

import com.kinetix.audit.model.AuditEvent
import com.kinetix.audit.module
import com.kinetix.audit.persistence.AuditEventRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.serialization.json.*
import java.time.Instant

class AuditRoutesTest : FunSpec({

    val repository = mockk<AuditEventRepository>()

    beforeEach { clearMocks(repository) }

    test("GET /api/v1/audit/events returns 200 with event list") {
        val events = listOf(
            AuditEvent(
                id = 1,
                tradeId = "t-1",
                portfolioId = "port-1",
                instrumentId = "AAPL",
                assetClass = "EQUITY",
                side = "BUY",
                quantity = "100",
                priceAmount = "150.00",
                priceCurrency = "USD",
                tradedAt = "2025-01-15T10:00:00Z",
                receivedAt = Instant.parse("2025-01-15T10:00:01Z"),
            ),
            AuditEvent(
                id = 2,
                tradeId = "t-2",
                portfolioId = "port-1",
                instrumentId = "MSFT",
                assetClass = "EQUITY",
                side = "SELL",
                quantity = "50",
                priceAmount = "300.00",
                priceCurrency = "USD",
                tradedAt = "2025-01-15T11:00:00Z",
                receivedAt = Instant.parse("2025-01-15T11:00:01Z"),
            ),
        )
        coEvery { repository.findAll() } returns events

        testApplication {
            application { module(repository) }
            val response = client.get("/api/v1/audit/events")
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body.size shouldBe 2
            body[0].jsonObject["tradeId"]?.jsonPrimitive?.content shouldBe "t-1"
            body[0].jsonObject["portfolioId"]?.jsonPrimitive?.content shouldBe "port-1"
            body[0].jsonObject["instrumentId"]?.jsonPrimitive?.content shouldBe "AAPL"
            body[1].jsonObject["tradeId"]?.jsonPrimitive?.content shouldBe "t-2"
        }
    }

    test("GET /api/v1/audit/events returns empty array when no events") {
        coEvery { repository.findAll() } returns emptyList()

        testApplication {
            application { module(repository) }
            val response = client.get("/api/v1/audit/events")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe "[]"
        }
    }

    test("GET /api/v1/audit/events?portfolioId=X filters by portfolio") {
        val event = AuditEvent(
            id = 1,
            tradeId = "t-1",
            portfolioId = "port-1",
            instrumentId = "AAPL",
            assetClass = "EQUITY",
            side = "BUY",
            quantity = "100",
            priceAmount = "150.00",
            priceCurrency = "USD",
            tradedAt = "2025-01-15T10:00:00Z",
            receivedAt = Instant.parse("2025-01-15T10:00:01Z"),
        )
        coEvery { repository.findByPortfolioId("port-1") } returns listOf(event)

        testApplication {
            application { module(repository) }
            val response = client.get("/api/v1/audit/events?portfolioId=port-1")
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body.size shouldBe 1
            body[0].jsonObject["portfolioId"]?.jsonPrimitive?.content shouldBe "port-1"

            coVerify(exactly = 1) { repository.findByPortfolioId("port-1") }
            coVerify(exactly = 0) { repository.findAll() }
        }
    }

    test("GET /health still returns 200 when audit routes are installed") {
        testApplication {
            application { module(repository) }
            val response = client.get("/health")
            response.status shouldBe HttpStatusCode.OK
        }
    }
})
