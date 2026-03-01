package com.kinetix.regulatory.stress

import com.kinetix.regulatory.client.RiskOrchestratorClient
import com.kinetix.regulatory.module
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

class StressScenarioRoutesTest : FunSpec({

    val repository = mockk<StressScenarioRepository>()
    val service = StressScenarioService(repository)

    test("POST /api/v1/stress-scenarios creates a new scenario") {
        coEvery { repository.save(any()) } returns Unit

        testApplication {
            application {
                module(mockk<FrtbCalculationRepository>(), mockk<RiskOrchestratorClient>())
                routing { stressScenarioRoutes(service) }
            }
            val response = client.post("/api/v1/stress-scenarios") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"2008 Crisis","description":"Financial crisis scenario","shocks":"{}","createdBy":"analyst-1"}""")
            }
            response.status shouldBe HttpStatusCode.Created
            val body = response.bodyAsText()
            body shouldContain "\"name\":\"2008 Crisis\""
            body shouldContain "\"status\":\"DRAFT\""
        }
    }

    test("GET /api/v1/stress-scenarios lists all scenarios") {
        val scenarios = listOf(
            StressScenario(
                id = UUID.randomUUID().toString(),
                name = "Crisis Scenario",
                description = "Test",
                shocks = "{}",
                status = ScenarioStatus.DRAFT,
                createdBy = "analyst-1",
                approvedBy = null,
                approvedAt = null,
                createdAt = Instant.now(),
            ),
        )
        coEvery { repository.findAll() } returns scenarios

        testApplication {
            application {
                module(mockk<FrtbCalculationRepository>(), mockk<RiskOrchestratorClient>())
                routing { stressScenarioRoutes(service) }
            }
            val response = client.get("/api/v1/stress-scenarios")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "\"name\":\"Crisis Scenario\""
        }
    }

    test("GET /api/v1/stress-scenarios/approved lists only approved scenarios") {
        val scenarios = listOf(
            StressScenario(
                id = UUID.randomUUID().toString(),
                name = "Approved Scenario",
                description = "Test",
                shocks = "{}",
                status = ScenarioStatus.APPROVED,
                createdBy = "analyst-1",
                approvedBy = "manager-1",
                approvedAt = Instant.now(),
                createdAt = Instant.now(),
            ),
        )
        coEvery { repository.findByStatus(ScenarioStatus.APPROVED) } returns scenarios

        testApplication {
            application {
                module(mockk<FrtbCalculationRepository>(), mockk<RiskOrchestratorClient>())
                routing { stressScenarioRoutes(service) }
            }
            val response = client.get("/api/v1/stress-scenarios/approved")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "\"name\":\"Approved Scenario\""
            body shouldContain "\"status\":\"APPROVED\""
        }
    }

    test("PATCH /api/v1/stress-scenarios/{id}/approve approves a scenario") {
        val id = UUID.randomUUID().toString()
        val scenario = StressScenario(
            id = id,
            name = "Test Scenario",
            description = "Test",
            shocks = "{}",
            status = ScenarioStatus.PENDING_APPROVAL,
            createdBy = "analyst-1",
            approvedBy = null,
            approvedAt = null,
            createdAt = Instant.now(),
        )
        coEvery { repository.findById(id) } returns scenario
        coEvery { repository.save(any()) } returns Unit

        testApplication {
            application {
                module(mockk<FrtbCalculationRepository>(), mockk<RiskOrchestratorClient>())
                routing { stressScenarioRoutes(service) }
            }
            val response = client.patch("/api/v1/stress-scenarios/$id/approve") {
                contentType(ContentType.Application.Json)
                setBody("""{"approvedBy":"manager-1"}""")
            }
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "\"status\":\"APPROVED\""
            body shouldContain "\"approvedBy\":\"manager-1\""
        }
    }

    test("PATCH /api/v1/stress-scenarios/{id}/retire retires a scenario") {
        val id = UUID.randomUUID().toString()
        val scenario = StressScenario(
            id = id,
            name = "Old Scenario",
            description = "Test",
            shocks = "{}",
            status = ScenarioStatus.APPROVED,
            createdBy = "analyst-1",
            approvedBy = "manager-1",
            approvedAt = Instant.now(),
            createdAt = Instant.now(),
        )
        coEvery { repository.findById(id) } returns scenario
        coEvery { repository.save(any()) } returns Unit

        testApplication {
            application {
                module(mockk<FrtbCalculationRepository>(), mockk<RiskOrchestratorClient>())
                routing { stressScenarioRoutes(service) }
            }
            val response = client.patch("/api/v1/stress-scenarios/$id/retire")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "\"status\":\"RETIRED\""
        }
    }
})
