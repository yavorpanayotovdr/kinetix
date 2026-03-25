package com.kinetix.regulatory.historical

import com.kinetix.regulatory.client.RiskOrchestratorClient
import com.kinetix.regulatory.module
import com.kinetix.regulatory.persistence.DatabaseTestSetup
import com.kinetix.regulatory.persistence.ExposedFrtbCalculationRepository
import com.kinetix.regulatory.persistence.ExposedHistoricalScenarioRepository
import com.kinetix.regulatory.persistence.HistoricalScenarioPeriodsTable
import com.kinetix.regulatory.persistence.HistoricalScenarioReturnsTable
import com.kinetix.regulatory.seed.HistoricalScenarioSeeder
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldNotBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.mockk
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class HistoricalScenarioPeriodAcceptanceTest : FunSpec({

    val db = DatabaseTestSetup.startAndMigrate()
    val frtbRepo = ExposedFrtbCalculationRepository(db)
    val riskClient = mockk<RiskOrchestratorClient>()
    val historicalRepo = ExposedHistoricalScenarioRepository(db)
    val seeder = HistoricalScenarioSeeder(historicalRepo)

    beforeEach {
        newSuspendedTransaction(db = db) {
            HistoricalScenarioReturnsTable.deleteAll()
            HistoricalScenarioPeriodsTable.deleteAll()
        }
        seeder.seed()
    }

    test("GET /historical-periods returns seeded crisis periods") {
        testApplication {
            application {
                module(frtbRepo, riskClient, historicalScenarioRepository = historicalRepo)
            }
            val response = client.get("/api/v1/historical-periods")

            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body shouldHaveSize 4

            val periodIds = body.map { it.jsonObject["periodId"]!!.jsonPrimitive.content }.toSet()
            periodIds shouldBe setOf("GFC_OCT_2008", "COVID_MAR_2020", "TAPER_TANTRUM_2013", "EURO_CRISIS_2011")
        }
    }

    test("GET /historical-periods/{periodId} returns the specified period") {
        testApplication {
            application {
                module(frtbRepo, riskClient, historicalScenarioRepository = historicalRepo)
            }
            val response = client.get("/api/v1/historical-periods/GFC_OCT_2008")

            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["periodId"]?.jsonPrimitive?.content shouldBe "GFC_OCT_2008"
            body["name"]?.jsonPrimitive?.content shouldBe "GFC_OCT_2008"
            body["startDate"]?.jsonPrimitive?.content shouldBe "2008-09-15"
            body["endDate"]?.jsonPrimitive?.content shouldBe "2009-03-15"
            body["severityLabel"]?.jsonPrimitive?.content shouldBe "SEVERE"
            body["assetClassFocus"]?.jsonPrimitive?.content shouldBe "EQUITY"
            body["description"] shouldNotBe null
        }
    }

    test("GET /historical-periods/{periodId} returns 404 for unknown period") {
        testApplication {
            application {
                module(frtbRepo, riskClient, historicalScenarioRepository = historicalRepo)
            }
            val response = client.get("/api/v1/historical-periods/UNKNOWN_PERIOD_XYZ")

            response.status shouldBe HttpStatusCode.NotFound
        }
    }
})
