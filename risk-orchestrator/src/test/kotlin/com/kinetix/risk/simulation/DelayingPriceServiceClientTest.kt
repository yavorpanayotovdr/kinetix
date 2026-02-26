package com.kinetix.risk.simulation

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.PricePoint
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

    test("delegates getLatestPrice and returns result") {
        coEvery { delegate.getLatestPrice(instrumentId) } returns pricePoint

        val result = client.getLatestPrice(instrumentId)

        result shouldBe pricePoint
    }

    test("applies delay to getLatestPrice") {
        coEvery { delegate.getLatestPrice(instrumentId) } returns pricePoint

        val elapsed = measureTimeMillis {
            client.getLatestPrice(instrumentId)
        }

        elapsed shouldBeGreaterThanOrEqual delayRange.first
    }

    test("delegates getPriceHistory and returns result") {
        val from = Instant.parse("2025-01-01T00:00:00Z")
        val to = Instant.parse("2025-01-15T00:00:00Z")
        val history = listOf(pricePoint)
        coEvery { delegate.getPriceHistory(instrumentId, from, to) } returns history

        val result = client.getPriceHistory(instrumentId, from, to)

        result shouldBe history
    }

    test("applies delay to getPriceHistory") {
        val from = Instant.parse("2025-01-01T00:00:00Z")
        val to = Instant.parse("2025-01-15T00:00:00Z")
        coEvery { delegate.getPriceHistory(instrumentId, from, to) } returns emptyList()

        val elapsed = measureTimeMillis {
            client.getPriceHistory(instrumentId, from, to)
        }

        elapsed shouldBeGreaterThanOrEqual delayRange.first
    }
})
