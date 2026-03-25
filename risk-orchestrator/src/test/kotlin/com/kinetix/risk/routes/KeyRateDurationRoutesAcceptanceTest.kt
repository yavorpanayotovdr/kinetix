package com.kinetix.risk.routes

import com.kinetix.common.model.BookId
import com.kinetix.risk.model.InstrumentKrdResult
import com.kinetix.risk.model.KrdBucket
import com.kinetix.risk.service.KeyRateDurationService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal

private fun sampleKrdResult(instrumentId: String = "UST-10Y") = InstrumentKrdResult(
    instrumentId = instrumentId,
    krdBuckets = listOf(
        KrdBucket("2Y", 730, BigDecimal("45.12")),
        KrdBucket("5Y", 1825, BigDecimal("120.34")),
        KrdBucket("10Y", 3650, BigDecimal("680.55")),
        KrdBucket("30Y", 10950, BigDecimal("8.01")),
    ),
    totalDv01 = BigDecimal("854.02"),
)

class KeyRateDurationRoutesAcceptanceTest : FunSpec({

    val service = mockk<KeyRateDurationService>()

    fun testApp(block: suspend ApplicationTestBuilder.() -> Unit) {
        testApplication {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing {
                keyRateDurationRoutes(service)
            }
            block()
        }
    }

    test("GET /api/v1/risk/krd/{bookId} returns 200") {
        coEvery { service.calculate(BookId("BOOK-1")) } returns listOf(sampleKrdResult())

        testApp {
            val response = client.get("/api/v1/risk/krd/BOOK-1")
            response.status shouldBe HttpStatusCode.OK
        }
    }

    test("GET /api/v1/risk/krd/{bookId} response contains instruments array") {
        coEvery { service.calculate(BookId("BOOK-1")) } returns listOf(sampleKrdResult("UST-10Y"))

        testApp {
            val response = client.get("/api/v1/risk/krd/BOOK-1")
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["instruments"]!!.jsonArray.size shouldBe 1
        }
    }

    test("GET /api/v1/risk/krd/{bookId} response contains correct instrument id") {
        coEvery { service.calculate(BookId("BOOK-1")) } returns listOf(sampleKrdResult("UST-10Y"))

        testApp {
            val response = client.get("/api/v1/risk/krd/BOOK-1")
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val firstInstrument = body["instruments"]!!.jsonArray[0].jsonObject
            firstInstrument["instrumentId"]!!.jsonPrimitive.content shouldBe "UST-10Y"
        }
    }

    test("GET /api/v1/risk/krd/{bookId} response instrument has four krdBuckets") {
        coEvery { service.calculate(BookId("BOOK-1")) } returns listOf(sampleKrdResult())

        testApp {
            val response = client.get("/api/v1/risk/krd/BOOK-1")
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val firstInstrument = body["instruments"]!!.jsonArray[0].jsonObject
            firstInstrument["krdBuckets"]!!.jsonArray.size shouldBe 4
        }
    }

    test("GET /api/v1/risk/krd/{bookId} response contains aggregated buckets") {
        coEvery { service.calculate(BookId("BOOK-1")) } returns listOf(
            sampleKrdResult("UST-10Y"),
            sampleKrdResult("UST-5Y"),
        )

        testApp {
            val response = client.get("/api/v1/risk/krd/BOOK-1")
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val aggregated = body["aggregated"]!!.jsonArray
            aggregated.size shouldBe 4
        }
    }

    test("GET /api/v1/risk/krd/{bookId} aggregated 10Y bucket sums DV01 across instruments") {
        coEvery { service.calculate(BookId("BOOK-1")) } returns listOf(
            sampleKrdResult("UST-10Y"),
            sampleKrdResult("UST-5Y"),
        )

        testApp {
            val response = client.get("/api/v1/risk/krd/BOOK-1")
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val aggregated = body["aggregated"]!!.jsonArray
            val tenYearBucket = aggregated.map { it.jsonObject }.first { it["tenorLabel"]!!.jsonPrimitive.content == "10Y" }
            // Two instruments each with 680.55 at 10Y => total 1361.10
            val tenYDv01 = tenYearBucket["dv01"]!!.jsonPrimitive.content.toBigDecimal()
            tenYDv01.compareTo(BigDecimal("1361.10")) shouldBe 0
        }
    }

    test("GET /api/v1/risk/krd/{bookId} returns empty instruments array when no fixed-income positions") {
        coEvery { service.calculate(BookId("EMPTY")) } returns emptyList()

        testApp {
            val response = client.get("/api/v1/risk/krd/EMPTY")
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["instruments"]!!.jsonArray.size shouldBe 0
        }
    }
})
