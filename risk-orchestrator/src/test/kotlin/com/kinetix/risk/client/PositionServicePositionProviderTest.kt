package com.kinetix.risk.client

import com.kinetix.common.model.*
import com.kinetix.position.persistence.PositionRepository
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

    val repository = mockk<PositionRepository>()
    val provider = PositionServicePositionProvider(repository)

    test("delegates to PositionRepository.findByPortfolioId") {
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

        coEvery { repository.findByPortfolioId(portfolioId) } returns positions

        val result = provider.getPositions(portfolioId)

        result shouldHaveSize 1
        result[0].instrumentId shouldBe InstrumentId("AAPL")
        coVerify(exactly = 1) { repository.findByPortfolioId(portfolioId) }
    }

    test("returns empty list when no positions exist") {
        val portfolioId = PortfolioId("empty-port")
        coEvery { repository.findByPortfolioId(portfolioId) } returns emptyList()

        val result = provider.getPositions(portfolioId)

        result shouldHaveSize 0
    }
})
