package com.kinetix.regulatory.stress

import com.kinetix.regulatory.client.RiskOrchestratorClient
import com.kinetix.regulatory.module
import com.kinetix.regulatory.persistence.DatabaseTestSetup
import com.kinetix.regulatory.persistence.ExposedFrtbCalculationRepository
import com.kinetix.regulatory.persistence.ExposedStressScenarioRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.mockk

class StressScenarioProductionWiringAcceptanceTest : BehaviorSpec({

    val db = DatabaseTestSetup.startAndMigrate()
    val frtbRepo = ExposedFrtbCalculationRepository(db)
    val riskClient = mockk<RiskOrchestratorClient>()

    given("stress scenario repository is not provided") {
        `when`("GET /api/v1/stress-scenarios") {
            then("returns 404 because routes are not registered") {
                testApplication {
                    application {
                        module(frtbRepo, riskClient, backtestRepository = null, stressScenarioRepository = null)
                    }
                    val response = client.get("/api/v1/stress-scenarios")
                    response.status shouldBe HttpStatusCode.NotFound
                }
            }
        }
    }

    given("stress scenario repository is provided") {
        `when`("GET /api/v1/stress-scenarios") {
            then("returns 200 with empty list") {
                testApplication {
                    application {
                        module(frtbRepo, riskClient, stressScenarioRepository = ExposedStressScenarioRepository(db))
                    }
                    val response = client.get("/api/v1/stress-scenarios")
                    response.status shouldBe HttpStatusCode.OK
                }
            }
        }
    }
})
