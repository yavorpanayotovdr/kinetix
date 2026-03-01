package com.kinetix.referencedata.routes

import com.kinetix.common.model.CreditSpread
import com.kinetix.common.model.DividendYield
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.ReferenceDataSource
import com.kinetix.referencedata.module
import com.kinetix.referencedata.persistence.CreditSpreadRepository
import com.kinetix.referencedata.persistence.DividendYieldRepository
import com.kinetix.referencedata.service.ReferenceDataIngestionService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDate

/**
 * Contract tests verifying that the reference-data-service HTTP response shape can be
 * consumed by the risk-orchestrator's HttpReferenceDataServiceClient DTOs.
 *
 * The orchestrator expects:
 *   DividendYieldDto { instrumentId, yield:Double, exDate:String?, asOfDate, source }
 *   CreditSpreadDto  { instrumentId, spread:Double, rating:String?, asOfDate, source }
 */
class ReferenceDataServiceAcceptanceTest : FunSpec({

    val dividendYieldRepo = mockk<DividendYieldRepository>()
    val creditSpreadRepo = mockk<CreditSpreadRepository>()
    val ingestionService = mockk<ReferenceDataIngestionService>()

    val AS_OF = Instant.parse("2026-01-15T12:00:00Z")

    test("dividend yield response shape matches DividendYieldDto consumed by risk-orchestrator") {
        val dividendYield = DividendYield(
            instrumentId = InstrumentId("AAPL"),
            yield = 0.0065,
            exDate = LocalDate.of(2026, 3, 15),
            asOfDate = AS_OF,
            source = ReferenceDataSource.BLOOMBERG,
        )
        coEvery { dividendYieldRepo.findLatest(InstrumentId("AAPL")) } returns dividendYield

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService) }

            val response = client.get("/api/v1/reference-data/dividends/AAPL/latest")
            response.status shouldBe HttpStatusCode.OK

            val body: JsonObject = Json.parseToJsonElement(response.bodyAsText()).jsonObject

            // Fields the orchestrator's DividendYieldDto requires
            body["instrumentId"]?.jsonPrimitive?.content shouldBe "AAPL"
            body["yield"]?.jsonPrimitive?.double shouldBe 0.0065
            // exDate is nullable String — orchestrator parses via LocalDate.parse(it)
            body["exDate"]?.jsonPrimitive?.content shouldBe "2026-03-15"
            body["asOfDate"]?.jsonPrimitive?.content shouldBe AS_OF.toString()
            body["source"]?.jsonPrimitive?.content shouldBe "BLOOMBERG"
        }
    }

    test("dividend yield response serializes null exDate correctly for risk-orchestrator") {
        val dividendYield = DividendYield(
            instrumentId = InstrumentId("MSFT"),
            yield = 0.0081,
            exDate = null,
            asOfDate = AS_OF,
            source = ReferenceDataSource.REUTERS,
        )
        coEvery { dividendYieldRepo.findLatest(InstrumentId("MSFT")) } returns dividendYield

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService) }

            val response = client.get("/api/v1/reference-data/dividends/MSFT/latest")
            response.status shouldBe HttpStatusCode.OK

            val body: JsonObject = Json.parseToJsonElement(response.bodyAsText()).jsonObject

            body["instrumentId"]?.jsonPrimitive?.content shouldBe "MSFT"
            body["yield"]?.jsonPrimitive?.double shouldBe 0.0081
            // orchestrator DTO declares exDate as String? — null in JSON is acceptable
            body.containsKey("exDate") shouldBe true
            body["source"]?.jsonPrimitive?.content shouldBe "REUTERS"
        }
    }

    test("credit spread response shape matches CreditSpreadDto consumed by risk-orchestrator") {
        val creditSpread = CreditSpread(
            instrumentId = InstrumentId("CORP-BOND-1"),
            spread = 0.0125,
            rating = "AA",
            asOfDate = AS_OF,
            source = ReferenceDataSource.RATING_AGENCY,
        )
        coEvery { creditSpreadRepo.findLatest(InstrumentId("CORP-BOND-1")) } returns creditSpread

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService) }

            val response = client.get("/api/v1/reference-data/credit-spreads/CORP-BOND-1/latest")
            response.status shouldBe HttpStatusCode.OK

            val body: JsonObject = Json.parseToJsonElement(response.bodyAsText()).jsonObject

            // Fields the orchestrator's CreditSpreadDto requires
            body["instrumentId"]?.jsonPrimitive?.content shouldBe "CORP-BOND-1"
            body["spread"]?.jsonPrimitive?.double shouldBe 0.0125
            body["rating"]?.jsonPrimitive?.content shouldBe "AA"
            body["asOfDate"]?.jsonPrimitive?.content shouldBe AS_OF.toString()
            body["source"]?.jsonPrimitive?.content shouldBe "RATING_AGENCY"
        }
    }

    test("credit spread response serializes null rating correctly for risk-orchestrator") {
        val creditSpread = CreditSpread(
            instrumentId = InstrumentId("BOND-NR"),
            spread = 0.0250,
            rating = null,
            asOfDate = AS_OF,
            source = ReferenceDataSource.INTERNAL,
        )
        coEvery { creditSpreadRepo.findLatest(InstrumentId("BOND-NR")) } returns creditSpread

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService) }

            val response = client.get("/api/v1/reference-data/credit-spreads/BOND-NR/latest")
            response.status shouldBe HttpStatusCode.OK

            val body: JsonObject = Json.parseToJsonElement(response.bodyAsText()).jsonObject

            body["instrumentId"]?.jsonPrimitive?.content shouldBe "BOND-NR"
            body["spread"]?.jsonPrimitive?.double shouldBe 0.0250
            // orchestrator DTO declares rating as String? — null in JSON is acceptable
            body.containsKey("rating") shouldBe true
            body["source"]?.jsonPrimitive?.content shouldBe "INTERNAL"
        }
    }

    test("dividend yield endpoint returns 404 when instrument does not exist") {
        coEvery { dividendYieldRepo.findLatest(InstrumentId("UNKNOWN")) } returns null

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService) }

            val response = client.get("/api/v1/reference-data/dividends/UNKNOWN/latest")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("credit spread endpoint returns 404 when instrument does not exist") {
        coEvery { creditSpreadRepo.findLatest(InstrumentId("UNKNOWN")) } returns null

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService) }

            val response = client.get("/api/v1/reference-data/credit-spreads/UNKNOWN/latest")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }
})
