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
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate

private val NOW = Instant.parse("2026-02-24T10:00:00Z")

class ReferenceDataRoutesTest : FunSpec({

    val dividendYieldRepo = mockk<DividendYieldRepository>()
    val creditSpreadRepo = mockk<CreditSpreadRepository>()
    val ingestionService = mockk<ReferenceDataIngestionService>()

    beforeEach {
        clearMocks(dividendYieldRepo, creditSpreadRepo)
    }

    test("GET dividends latest returns 200 with yield") {
        val dividendYield = DividendYield(
            instrumentId = InstrumentId("AAPL"),
            yield = 0.0065,
            exDate = LocalDate.of(2026, 3, 15),
            asOfDate = NOW,
            source = ReferenceDataSource.BLOOMBERG,
        )
        coEvery { dividendYieldRepo.findLatest(InstrumentId("AAPL")) } returns dividendYield

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService) }

            val response = client.get("/api/v1/reference-data/dividends/AAPL/latest")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "AAPL"
            body shouldContain "0.0065"
            body shouldContain "BLOOMBERG"
        }
    }

    test("GET dividends latest returns 404 for unknown instrument") {
        coEvery { dividendYieldRepo.findLatest(InstrumentId("UNKNOWN")) } returns null

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService) }

            val response = client.get("/api/v1/reference-data/dividends/UNKNOWN/latest")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("GET credit-spreads latest returns 200 with spread") {
        val creditSpread = CreditSpread(
            instrumentId = InstrumentId("CORP-BOND-1"),
            spread = 0.0125,
            rating = "AA",
            asOfDate = NOW,
            source = ReferenceDataSource.RATING_AGENCY,
        )
        coEvery { creditSpreadRepo.findLatest(InstrumentId("CORP-BOND-1")) } returns creditSpread

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService) }

            val response = client.get("/api/v1/reference-data/credit-spreads/CORP-BOND-1/latest")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "CORP-BOND-1"
            body shouldContain "0.0125"
            body shouldContain "RATING_AGENCY"
        }
    }

    test("GET credit-spreads latest returns 404 for unknown instrument") {
        coEvery { creditSpreadRepo.findLatest(InstrumentId("UNKNOWN")) } returns null

        testApplication {
            application { module(dividendYieldRepo, creditSpreadRepo, ingestionService) }

            val response = client.get("/api/v1/reference-data/credit-spreads/UNKNOWN/latest")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }
})
