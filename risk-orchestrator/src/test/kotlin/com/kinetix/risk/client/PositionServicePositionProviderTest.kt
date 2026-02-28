package com.kinetix.risk.client

import com.kinetix.common.model.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigDecimal
import java.util.Currency

private val USD = Currency.getInstance("USD")

class PositionServicePositionProviderTest : FunSpec({

    val client = mockk<PositionServiceClient>()
    val provider = PositionServicePositionProvider(client)

    test("delegates to PositionServiceClient.getPositions") {
        val portfolioId = PortfolioId("port-1")
        val positions = listOf(
            Position(
                portfolioId = portfolioId,
                instrumentId = InstrumentId("AAPL"),
                assetClass = AssetClass.EQUITY,
                quantity = BigDecimal("100"),
                averageCost = Money(BigDecimal("150.00"), USD),
                marketPrice = Money(BigDecimal("170.00"), USD),
            ),
        )

        coEvery { client.getPositions(portfolioId) } returns ClientResponse.Success(positions)

        val result = provider.getPositions(portfolioId)

        result shouldHaveSize 1
        result[0].instrumentId shouldBe InstrumentId("AAPL")
        coVerify(exactly = 1) { client.getPositions(portfolioId) }
    }

    test("returns empty list when no positions exist") {
        val portfolioId = PortfolioId("empty-port")
        coEvery { client.getPositions(portfolioId) } returns ClientResponse.Success(emptyList())

        val result = provider.getPositions(portfolioId)

        result shouldHaveSize 0
    }

    test("returns empty list when client returns NotFound") {
        val portfolioId = PortfolioId("missing-port")
        coEvery { client.getPositions(portfolioId) } returns ClientResponse.NotFound(404)

        val result = provider.getPositions(portfolioId)

        result shouldHaveSize 0
    }
})
