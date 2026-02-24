package com.kinetix.volatility.routes

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.VolPoint
import com.kinetix.common.model.VolSurface
import com.kinetix.common.model.VolatilitySource
import com.kinetix.volatility.module
import com.kinetix.volatility.persistence.VolSurfaceRepository
import com.kinetix.volatility.service.VolatilityIngestionService
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

private val NOW = Instant.parse("2026-02-24T10:00:00Z")

class VolatilityRoutesTest : FunSpec({

    val volSurfaceRepo = mockk<VolSurfaceRepository>()
    val ingestionService = mockk<VolatilityIngestionService>()

    beforeEach {
        clearMocks(volSurfaceRepo)
    }

    test("GET surface latest returns 200 with surface") {
        val surface = VolSurface(
            instrumentId = InstrumentId("AAPL"),
            asOf = NOW,
            points = listOf(
                VolPoint(BigDecimal("100"), 30, BigDecimal("0.25")),
                VolPoint(BigDecimal("100"), 90, BigDecimal("0.28")),
            ),
            source = VolatilitySource.BLOOMBERG,
        )
        coEvery { volSurfaceRepo.findLatest(InstrumentId("AAPL")) } returns surface

        testApplication {
            application { module(volSurfaceRepo, ingestionService) }

            val response = client.get("/api/v1/volatility/AAPL/surface/latest")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "AAPL"
            body shouldContain "0.25"
            body shouldContain "BLOOMBERG"
        }
    }

    test("GET surface latest returns 404 for unknown instrument") {
        coEvery { volSurfaceRepo.findLatest(InstrumentId("UNKNOWN")) } returns null

        testApplication {
            application { module(volSurfaceRepo, ingestionService) }

            val response = client.get("/api/v1/volatility/UNKNOWN/surface/latest")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }
})
