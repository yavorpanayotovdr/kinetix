package com.kinetix.referencedata.routes

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.instrument.CashEquity
import com.kinetix.common.model.instrument.CommodityFuture
import com.kinetix.common.model.instrument.CorporateBond
import com.kinetix.common.model.instrument.EquityOption
import com.kinetix.common.model.instrument.FxSpot
import com.kinetix.common.model.instrument.GovernmentBond
import com.kinetix.referencedata.model.Instrument
import com.kinetix.referencedata.module
import com.kinetix.referencedata.persistence.CreditSpreadRepository
import com.kinetix.referencedata.persistence.DividendYieldRepository
import com.kinetix.referencedata.persistence.InstrumentRepository
import com.kinetix.referencedata.service.InstrumentService
import com.kinetix.referencedata.service.ReferenceDataIngestionService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

class InstrumentRoutesAcceptanceTest : FunSpec({

    val dividendYieldRepo = mockk<DividendYieldRepository>()
    val creditSpreadRepo = mockk<CreditSpreadRepository>()
    val ingestionService = mockk<ReferenceDataIngestionService>()
    val instrumentRepo = mockk<InstrumentRepository>()
    val instrumentService = InstrumentService(instrumentRepo)

    val NOW = Instant.parse("2026-03-16T12:00:00Z")

    test("GET instrument by ID returns correct shape for cash equity") {
        val instrument = Instrument(
            instrumentId = InstrumentId("AAPL"),
            instrumentType = CashEquity(currency = "USD", exchange = "NASDAQ", sector = "Technology", countryCode = "US"),
            displayName = "Apple Inc.",
            currency = "USD",
            createdAt = NOW,
            updatedAt = NOW,
        )
        coEvery { instrumentRepo.findById(InstrumentId("AAPL")) } returns instrument

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService, instrumentService) }

            val response = client.get("/api/v1/instruments/AAPL")
            response.status shouldBe HttpStatusCode.OK

            val body: JsonObject = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["instrumentId"]?.jsonPrimitive?.content shouldBe "AAPL"
            body["instrumentType"]?.jsonPrimitive?.content shouldBe "CASH_EQUITY"
            body["displayName"]?.jsonPrimitive?.content shouldBe "Apple Inc."
            body["assetClass"]?.jsonPrimitive?.content shouldBe "EQUITY"
            body["currency"]?.jsonPrimitive?.content shouldBe "USD"
            body["attributes"]?.jsonObject?.get("exchange")?.jsonPrimitive?.content shouldBe "NASDAQ"
        }
    }

    test("GET instrument by ID returns correct shape for equity option") {
        val instrument = Instrument(
            instrumentId = InstrumentId("AAPL-C-150-20260620"),
            instrumentType = EquityOption(
                underlyingId = "AAPL", optionType = "CALL", strike = 150.0,
                expiryDate = "2026-06-20", exerciseStyle = "EUROPEAN",
                contractMultiplier = 100.0, dividendYield = 0.005,
            ),
            displayName = "AAPL Call 150 Jun2026",
            currency = "USD",
            createdAt = NOW,
            updatedAt = NOW,
        )
        coEvery { instrumentRepo.findById(InstrumentId("AAPL-C-150-20260620")) } returns instrument

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService, instrumentService) }

            val response = client.get("/api/v1/instruments/AAPL-C-150-20260620")
            response.status shouldBe HttpStatusCode.OK

            val body: JsonObject = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["instrumentType"]?.jsonPrimitive?.content shouldBe "EQUITY_OPTION"
            body["assetClass"]?.jsonPrimitive?.content shouldBe "EQUITY"
            body["attributes"]?.jsonObject?.get("underlyingId")?.jsonPrimitive?.content shouldBe "AAPL"
        }
    }

    test("GET instrument by ID returns correct shape for government bond") {
        val instrument = Instrument(
            instrumentId = InstrumentId("US10Y"),
            instrumentType = GovernmentBond(
                currency = "USD", couponRate = 0.025, couponFrequency = 2,
                maturityDate = "2036-05-15", faceValue = 1000.0, dayCountConvention = "ACT/ACT",
            ),
            displayName = "US 10Y Treasury",
            currency = "USD",
            createdAt = NOW,
            updatedAt = NOW,
        )
        coEvery { instrumentRepo.findById(InstrumentId("US10Y")) } returns instrument

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService, instrumentService) }

            val response = client.get("/api/v1/instruments/US10Y")
            response.status shouldBe HttpStatusCode.OK

            val body: JsonObject = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["instrumentType"]?.jsonPrimitive?.content shouldBe "GOVERNMENT_BOND"
            body["assetClass"]?.jsonPrimitive?.content shouldBe "FIXED_INCOME"
        }
    }

    test("GET instrument returns 404 when not found") {
        coEvery { instrumentRepo.findById(InstrumentId("UNKNOWN")) } returns null

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService, instrumentService) }

            val response = client.get("/api/v1/instruments/UNKNOWN")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("GET instruments filtered by type returns list") {
        val instruments = listOf(
            Instrument(
                instrumentId = InstrumentId("EURUSD"),
                instrumentType = FxSpot(baseCurrency = "EUR", quoteCurrency = "USD"),
                displayName = "EUR/USD Spot",
                currency = "USD",
                createdAt = NOW,
                updatedAt = NOW,
            ),
        )
        coEvery { instrumentRepo.findByType("FX_SPOT") } returns instruments

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService, instrumentService) }

            val response = client.get("/api/v1/instruments?type=FX_SPOT")
            response.status shouldBe HttpStatusCode.OK

            val body: JsonArray = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body.size shouldBe 1
            body[0].jsonObject["instrumentType"]?.jsonPrimitive?.content shouldBe "FX_SPOT"
        }
    }

    test("GET instruments filtered by asset class returns list") {
        val instruments = listOf(
            Instrument(
                instrumentId = InstrumentId("WTI-AUG26"),
                instrumentType = CommodityFuture(commodity = "WTI", expiryDate = "2026-08-20", contractSize = 1000.0, currency = "USD"),
                displayName = "WTI Crude Aug2026",
                currency = "USD",
                createdAt = NOW,
                updatedAt = NOW,
            ),
        )
        coEvery { instrumentRepo.findByAssetClass(AssetClass.COMMODITY) } returns instruments

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService, instrumentService) }

            val response = client.get("/api/v1/instruments?assetClass=COMMODITY")
            response.status shouldBe HttpStatusCode.OK

            val body: JsonArray = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body.size shouldBe 1
            body[0].jsonObject["assetClass"]?.jsonPrimitive?.content shouldBe "COMMODITY"
        }
    }

    test("POST creates an instrument and returns 201") {
        val saved = slot<Instrument>()
        coEvery { instrumentRepo.save(capture(saved)) } returns Unit

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService, instrumentService) }

            val response = client.post("/api/v1/instruments") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "instrumentId": "JPM-BOND",
                        "instrumentType": "CORPORATE_BOND",
                        "displayName": "JPMorgan 4.5% 2031",
                        "currency": "USD",
                        "attributes": {
                            "type": "CORPORATE_BOND",
                            "currency": "USD",
                            "couponRate": 0.045,
                            "couponFrequency": 2,
                            "maturityDate": "2031-03-15",
                            "faceValue": 1000.0,
                            "issuer": "JPM",
                            "creditRating": "A",
                            "seniority": "SENIOR_UNSECURED"
                        }
                    }
                """.trimIndent())
            }
            response.status shouldBe HttpStatusCode.Created

            val body: JsonObject = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["instrumentId"]?.jsonPrimitive?.content shouldBe "JPM-BOND"
            body["instrumentType"]?.jsonPrimitive?.content shouldBe "CORPORATE_BOND"
            body["assetClass"]?.jsonPrimitive?.content shouldBe "FIXED_INCOME"

            coVerify { instrumentRepo.save(any()) }
            saved.captured.instrumentId.value shouldBe "JPM-BOND"
            (saved.captured.instrumentType as CorporateBond).issuer shouldBe "JPM"
        }
    }
})
