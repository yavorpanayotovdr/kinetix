package com.kinetix.risk.service

import com.kinetix.common.model.PortfolioId
import com.kinetix.risk.model.PnlAttribution
import com.kinetix.risk.model.PositionPnlAttribution
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate

class PnlAttributionService {

    private val tradingDaysPerYear = BigDecimal(252)
    private val half = BigDecimal("0.5")
    private val mc = MathContext(20, RoundingMode.HALF_UP)

    fun attribute(
        portfolioId: PortfolioId,
        positions: List<PositionPnlInput>,
        date: LocalDate = LocalDate.now(),
    ): PnlAttribution {
        val dt = BigDecimal.ONE.divide(tradingDaysPerYear, mc)

        val positionAttributions = positions.map { pos ->
            val deltaPnl = pos.delta.multiply(pos.priceChange, mc)
            val gammaPnl = half.multiply(pos.gamma, mc).multiply(pos.priceChange.pow(2), mc)
            val vegaPnl = pos.vega.multiply(pos.volChange, mc)
            val thetaPnl = pos.theta.multiply(dt, mc)
            val rhoPnl = pos.rho.multiply(pos.rateChange, mc)
            val explained = deltaPnl + gammaPnl + vegaPnl + thetaPnl + rhoPnl
            val unexplainedPnl = pos.totalPnl.subtract(explained, mc)

            PositionPnlAttribution(
                instrumentId = pos.instrumentId,
                assetClass = pos.assetClass,
                totalPnl = pos.totalPnl,
                deltaPnl = deltaPnl,
                gammaPnl = gammaPnl,
                vegaPnl = vegaPnl,
                thetaPnl = thetaPnl,
                rhoPnl = rhoPnl,
                unexplainedPnl = unexplainedPnl,
            )
        }

        val totalPnl = positionAttributions.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.totalPnl, mc) }
        val deltaPnl = positionAttributions.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.deltaPnl, mc) }
        val gammaPnl = positionAttributions.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.gammaPnl, mc) }
        val vegaPnl = positionAttributions.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.vegaPnl, mc) }
        val thetaPnl = positionAttributions.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.thetaPnl, mc) }
        val rhoPnl = positionAttributions.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.rhoPnl, mc) }
        val unexplainedPnl = positionAttributions.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.unexplainedPnl, mc) }

        return PnlAttribution(
            portfolioId = portfolioId,
            date = date,
            totalPnl = totalPnl,
            deltaPnl = deltaPnl,
            gammaPnl = gammaPnl,
            vegaPnl = vegaPnl,
            thetaPnl = thetaPnl,
            rhoPnl = rhoPnl,
            unexplainedPnl = unexplainedPnl,
            positionAttributions = positionAttributions,
            calculatedAt = Instant.now(),
        )
    }
}
