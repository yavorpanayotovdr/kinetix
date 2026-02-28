package com.kinetix.risk.service

import com.kinetix.common.model.*
import com.kinetix.risk.client.PositionProvider
import com.kinetix.risk.client.RiskEngineClient
import com.kinetix.risk.model.*
import java.math.BigDecimal
import java.math.MathContext
import java.time.Instant

class WhatIfAnalysisService(
    private val positionProvider: PositionProvider,
    private val riskEngineClient: RiskEngineClient,
) {
    fun applyHypotheticalTrades(
        positions: List<Position>,
        trades: List<HypotheticalTrade>,
    ): List<Position> {
        val positionsByInstrument = positions.associateBy { it.instrumentId }.toMutableMap()
        val portfolioId = positions.firstOrNull()?.portfolioId ?: PortfolioId("unknown")

        for (trade in trades) {
            val existing = positionsByInstrument[trade.instrumentId]
            if (existing != null) {
                val asTrade = Trade(
                    tradeId = TradeId("hypothetical"),
                    portfolioId = existing.portfolioId,
                    instrumentId = trade.instrumentId,
                    assetClass = trade.assetClass,
                    side = trade.side,
                    quantity = trade.quantity,
                    price = trade.price,
                    tradedAt = Instant.now(),
                )
                positionsByInstrument[trade.instrumentId] = existing.applyTrade(asTrade)
            } else {
                val signedQty = trade.quantity * trade.side.sign.toBigDecimal()
                val newPosition = Position(
                    portfolioId = portfolioId,
                    instrumentId = trade.instrumentId,
                    assetClass = trade.assetClass,
                    quantity = signedQty,
                    averageCost = trade.price,
                    marketPrice = trade.price,
                )
                positionsByInstrument[trade.instrumentId] = newPosition
            }
        }

        return positionsByInstrument.values.toList()
    }

    suspend fun analyzeWhatIf(
        portfolioId: PortfolioId,
        hypotheticalTrades: List<HypotheticalTrade>,
        calculationType: CalculationType,
        confidenceLevel: ConfidenceLevel,
    ): WhatIfResult {
        val positions = positionProvider.getPositions(portfolioId)
        val hypotheticalPositions = applyHypotheticalTrades(positions, hypotheticalTrades)

        val request = VaRCalculationRequest(
            portfolioId = portfolioId,
            calculationType = calculationType,
            confidenceLevel = confidenceLevel,
        )

        val baseResult = riskEngineClient.valuate(request, positions)
        val hypotheticalResult = riskEngineClient.valuate(request, hypotheticalPositions)

        val baseVaR = baseResult.varValue ?: 0.0
        val baseES = baseResult.expectedShortfall ?: 0.0
        val hypoVaR = hypotheticalResult.varValue ?: 0.0
        val hypoES = hypotheticalResult.expectedShortfall ?: 0.0

        return WhatIfResult(
            baseVaR = baseVaR,
            baseExpectedShortfall = baseES,
            baseGreeks = baseResult.greeks,
            basePositionRisk = baseResult.positionRisk,
            hypotheticalVaR = hypoVaR,
            hypotheticalExpectedShortfall = hypoES,
            hypotheticalGreeks = hypotheticalResult.greeks,
            hypotheticalPositionRisk = hypotheticalResult.positionRisk,
            varChange = hypoVaR - baseVaR,
            esChange = hypoES - baseES,
            calculatedAt = Instant.now(),
        )
    }
}
