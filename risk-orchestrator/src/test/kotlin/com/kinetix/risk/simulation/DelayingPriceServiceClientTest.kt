package com.kinetix.risk.simulation

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.PricePoint
import com.kinetix.risk.client.ClientResponse
import com.kinetix.risk.client.PriceServiceClient
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Instant
import kotlin.system.measureTimeMillis

class DelayingPriceServiceClientTest : FunSpec({

    val delegate = mockk<PriceServiceClient>()
    val delayRange = 100L..200L
    val client = DelayingPriceServiceClient(delegate, delayRange)

    val instrumentId = InstrumentId("AAPL")
    val pricePoint = mockk<PricePoint>()
    val successResponse = ClientResponse.Success(pricePoint)

    test("delegates getLatestPrice and returns result") {
        coEvery { delegate.getLatestPrice(instrumentId) } returns successResponse

        val result = client.getLatestPrice(instrumentId)

        result shouldBe successResponse
    }

    test("applies delay to getLatestPrice") {
        coEvery { delegate.getLatestPrice(instrumentId) } returns successResponse

        val elapsed = measureTimeMillis {
            client.getLatestPrice(instrumentId)
        }

        elapsed shouldBeGreaterThanOrEqual delayRange.first
    }

    test("delegates getPriceHistory and returns result") {
        val from = Instant.parse("2025-01-01T00:00:00Z")
        val to = Instant.parse("2025-01-15T00:00:00Z")
        val history = ClientResponse.Success(listOf(pricePoint))
        coEvery { delegate.getPriceHistory(instrumentId, from, to) } returns history

        val result = client.getPriceHistory(instrumentId, from, to)

        result shouldBe history
    }

    test("applies delay to getPriceHistory") {
        val from = Instant.parse("2025-01-01T00:00:00Z")
        val to = Instant.parse("2025-01-15T00:00:00Z")
        coEvery { delegate.getPriceHistory(instrumentId, from, to) } returns ClientResponse.Success(emptyList())

        val elapsed = measureTimeMillis {
            client.getPriceHistory(instrumentId, from, to)
        }

        elapsed shouldBeGreaterThanOrEqual delayRange.first
    }
})
