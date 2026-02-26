package com.kinetix.risk.simulation

import com.kinetix.common.model.PortfolioId
import com.kinetix.common.model.Position
import com.kinetix.risk.client.PositionProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.system.measureTimeMillis

class DelayingPositionProviderTest : FunSpec({

    val delegate = mockk<PositionProvider>()
    val delayRange = 100L..200L
    val provider = DelayingPositionProvider(delegate, delayRange)

    val portfolioId = PortfolioId("port-1")
    val positions = listOf(mockk<Position>())

    test("delegates getPositions to underlying provider and returns result") {
        coEvery { delegate.getPositions(portfolioId) } returns positions

        val result = provider.getPositions(portfolioId)

        result shouldBe positions
    }

    test("applies delay before returning") {
        coEvery { delegate.getPositions(portfolioId) } returns positions

        val elapsed = measureTimeMillis {
            provider.getPositions(portfolioId)
        }

        elapsed shouldBeGreaterThanOrEqual delayRange.first
    }
})
