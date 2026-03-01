package com.kinetix.position.service

import com.kinetix.common.model.Money
import com.kinetix.common.model.PortfolioId
import com.kinetix.position.model.CurrencyExposure
import com.kinetix.position.model.PortfolioSummary
import com.kinetix.position.persistence.PositionRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency

class PortfolioAggregationService(
    private val positionRepository: PositionRepository,
    private val fxRateProvider: FxRateProvider,
) {
    suspend fun aggregate(
        portfolioId: PortfolioId,
        baseCurrency: Currency = Currency.getInstance("USD"),
    ): PortfolioSummary {
        val positions = positionRepository.findByPortfolioId(portfolioId)

        val byCurrency = positions.groupBy { it.currency }

        var totalNavAmount = BigDecimal.ZERO
        var totalPnlAmount = BigDecimal.ZERO
        val breakdown = mutableListOf<CurrencyExposure>()

        for ((currency, currencyPositions) in byCurrency) {
            val localMarketValue = currencyPositions.fold(BigDecimal.ZERO) { acc, pos ->
                acc + pos.marketValue.amount
            }
            val localPnl = currencyPositions.fold(BigDecimal.ZERO) { acc, pos ->
                acc + pos.unrealizedPnl.amount
            }

            val fxRate = fxRateProvider.getRate(currency, baseCurrency)
                ?: throw IllegalStateException("No FX rate available for $currency -> $baseCurrency")

            val baseMarketValue = (localMarketValue * fxRate).setScale(2, RoundingMode.HALF_UP)
            val basePnl = (localPnl * fxRate).setScale(2, RoundingMode.HALF_UP)

            totalNavAmount += baseMarketValue
            totalPnlAmount += basePnl

            breakdown.add(
                CurrencyExposure(
                    currency = currency,
                    localValue = Money(localMarketValue, currency),
                    baseValue = Money(baseMarketValue, baseCurrency),
                    fxRate = fxRate,
                )
            )
        }

        return PortfolioSummary(
            portfolioId = portfolioId,
            baseCurrency = baseCurrency,
            totalNav = Money(totalNavAmount, baseCurrency),
            totalUnrealizedPnl = Money(totalPnlAmount, baseCurrency),
            currencyBreakdown = breakdown,
        )
    }
}
