package com.kinetix.rates.routes

import com.kinetix.common.model.*
import com.kinetix.rates.module
import com.kinetix.rates.persistence.ForwardCurveRepository
import com.kinetix.rates.persistence.RiskFreeRateRepository
import com.kinetix.rates.persistence.YieldCurveRepository
import com.kinetix.rates.service.RateIngestionService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

private val USD = Currency.getInstance("USD")
private val NOW = Instant.parse("2026-02-24T10:00:00Z")

class RatesRoutesTest : FunSpec({

    val yieldCurveRepo = mockk<YieldCurveRepository>()
    val riskFreeRateRepo = mockk<RiskFreeRateRepository>()
    val forwardCurveRepo = mockk<ForwardCurveRepository>()
    val ingestionService = mockk<RateIngestionService>()

    beforeEach {
        clearMocks(yieldCurveRepo, riskFreeRateRepo, forwardCurveRepo)
    }

    test("GET yield-curves latest returns 200 with curve") {
        val curve = YieldCurve(
            currency = USD,
            asOf = NOW,
            tenors = listOf(
                Tenor.oneMonth(BigDecimal("0.04")),
                Tenor.oneYear(BigDecimal("0.05")),
            ),
            curveId = "USD-TREASURY",
            source = RateSource.CENTRAL_BANK,
        )
        coEvery { yieldCurveRepo.findLatest("USD-TREASURY") } returns curve

        testApplication {
            application { module(yieldCurveRepo, riskFreeRateRepo, forwardCurveRepo, ingestionService) }

            val response = client.get("/api/v1/rates/yield-curves/USD-TREASURY/latest")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "USD-TREASURY"
            body shouldContain "CENTRAL_BANK"
        }
    }

    test("GET yield-curves latest returns 404 for unknown curve") {
        coEvery { yieldCurveRepo.findLatest("UNKNOWN") } returns null

        testApplication {
            application { module(yieldCurveRepo, riskFreeRateRepo, forwardCurveRepo, ingestionService) }

            val response = client.get("/api/v1/rates/yield-curves/UNKNOWN/latest")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("GET risk-free rate latest returns 200 with rate") {
        val rate = RiskFreeRate(
            currency = USD,
            tenor = "3M",
            rate = 0.0525,
            asOfDate = NOW,
            source = RateSource.CENTRAL_BANK,
        )
        coEvery { riskFreeRateRepo.findLatest(USD, "3M") } returns rate

        testApplication {
            application { module(yieldCurveRepo, riskFreeRateRepo, forwardCurveRepo, ingestionService) }

            val response = client.get("/api/v1/rates/risk-free/USD/latest?tenor=3M")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "0.0525"
            body shouldContain "CENTRAL_BANK"
        }
    }

    test("GET risk-free rate latest returns 404 for unknown currency") {
        coEvery { riskFreeRateRepo.findLatest(any(), any()) } returns null

        testApplication {
            application { module(yieldCurveRepo, riskFreeRateRepo, forwardCurveRepo, ingestionService) }

            val response = client.get("/api/v1/rates/risk-free/JPY/latest?tenor=O/N")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("GET forward curve latest returns 200 with curve") {
        val curve = ForwardCurve(
            instrumentId = InstrumentId("EURUSD"),
            assetClass = "FX",
            points = listOf(
                CurvePoint("1M", 1.0855),
                CurvePoint("3M", 1.0870),
            ),
            asOfDate = NOW,
            source = RateSource.REUTERS,
        )
        coEvery { forwardCurveRepo.findLatest(InstrumentId("EURUSD")) } returns curve

        testApplication {
            application { module(yieldCurveRepo, riskFreeRateRepo, forwardCurveRepo, ingestionService) }

            val response = client.get("/api/v1/rates/forwards/EURUSD/latest")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "EURUSD"
            body shouldContain "REUTERS"
        }
    }

    test("GET forward curve latest returns 404 for unknown instrument") {
        coEvery { forwardCurveRepo.findLatest(InstrumentId("UNKNOWN")) } returns null

        testApplication {
            application { module(yieldCurveRepo, riskFreeRateRepo, forwardCurveRepo, ingestionService) }

            val response = client.get("/api/v1/rates/forwards/UNKNOWN/latest")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }
})
