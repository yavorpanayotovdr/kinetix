package com.kinetix.regulatory.governance

import com.kinetix.regulatory.module
import com.kinetix.regulatory.client.RiskOrchestratorClient
import com.kinetix.regulatory.persistence.FrtbCalculationRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Instant
import java.util.UUID

class ModelGovernanceRoutesTest : FunSpec({

    val repository = mockk<ModelVersionRepository>()
    val registry = ModelRegistry(repository)

    test("POST /api/v1/models registers a new model version") {
        coEvery { repository.save(any()) } returns Unit

        testApplication {
            application {
                module(mockk<FrtbCalculationRepository>(), mockk<RiskOrchestratorClient>())
                routing { modelGovernanceRoutes(registry) }
            }
            val response = client.post("/api/v1/models") {
                contentType(ContentType.Application.Json)
                setBody("""{"modelName":"HistoricalVaR","version":"1.0.0","parameters":"{}"}""")
            }
            response.status shouldBe HttpStatusCode.Created
            val body = response.bodyAsText()
            body shouldContain "\"modelName\":\"HistoricalVaR\""
            body shouldContain "\"status\":\"DRAFT\""
        }
    }

    test("GET /api/v1/models lists all model versions") {
        val models = listOf(
            ModelVersion(
                id = UUID.randomUUID().toString(),
                modelName = "HistoricalVaR",
                version = "1.0.0",
                status = ModelVersionStatus.DRAFT,
                parameters = "{}",
                approvedBy = null,
                approvedAt = null,
                createdAt = Instant.now(),
            ),
        )
        coEvery { repository.findAll() } returns models

        testApplication {
            application {
                module(mockk<FrtbCalculationRepository>(), mockk<RiskOrchestratorClient>())
                routing { modelGovernanceRoutes(registry) }
            }
            val response = client.get("/api/v1/models")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "\"modelName\":\"HistoricalVaR\""
        }
    }

    test("PATCH /api/v1/models/{id}/status transitions model status") {
        val id = UUID.randomUUID().toString()
        val model = ModelVersion(
            id = id,
            modelName = "HistoricalVaR",
            version = "1.0.0",
            status = ModelVersionStatus.DRAFT,
            parameters = "{}",
            approvedBy = null,
            approvedAt = null,
            createdAt = Instant.now(),
        )
        coEvery { repository.findById(id) } returns model
        coEvery { repository.save(any()) } returns Unit

        testApplication {
            application {
                module(mockk<FrtbCalculationRepository>(), mockk<RiskOrchestratorClient>())
                routing { modelGovernanceRoutes(registry) }
            }
            val response = client.patch("/api/v1/models/$id/status") {
                contentType(ContentType.Application.Json)
                setBody("""{"targetStatus":"VALIDATED"}""")
            }
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "\"status\":\"VALIDATED\""
        }
    }
})
