package com.kinetix.risk.service

import com.kinetix.common.model.BookId
import com.kinetix.risk.model.AttributionDataQuality
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
        bookId: BookId,
        positions: List<PositionPnlInput>,
        date: LocalDate = LocalDate.now(),
        correlations: Map<Pair<String, String>, Double> = emptyMap(),
        currency: String = "USD",
    ): PnlAttribution {
        val dt = BigDecimal.ONE.divide(tradingDaysPerYear, mc)

        // Pre-compute cross-gamma P&L for each position based on correlation with other instruments.
        // cross_gamma_ij = corr(i,j) * gamma_i * dS_i * gamma_j * dS_j
        // Each position's share: sum over all j≠i of the pairwise term, split evenly between i and j.
        val crossGammaByPosition = computeCrossGamma(positions, correlations)

        val positionAttributions = positions.map { pos ->
            // First-order Taylor terms
            val deltaPnl = pos.delta.multiply(pos.priceChange, mc)
            val gammaPnl = half.multiply(pos.gamma, mc).multiply(pos.priceChange.pow(2), mc)
            val vegaPnl = pos.vega.multiply(pos.volChange, mc)
            val thetaPnl = pos.theta.multiply(dt, mc)
            val rhoPnl = pos.rho.multiply(pos.rateChange, mc)

            // Cross-Greek (second-order mixed) terms
            val vannaPnl = pos.vanna.multiply(pos.priceChange, mc).multiply(pos.volChange, mc)
            val volgaPnl = half.multiply(pos.volga, mc).multiply(pos.volChange.pow(2), mc)
            val charmPnl = pos.charm.multiply(pos.priceChange, mc).multiply(dt, mc)
            val crossGammaPnl = crossGammaByPosition[pos.instrumentId.value] ?: BigDecimal.ZERO

            val explained = deltaPnl + gammaPnl + vegaPnl + thetaPnl + rhoPnl +
                vannaPnl + volgaPnl + charmPnl + crossGammaPnl
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
                vannaPnl = vannaPnl,
                volgaPnl = volgaPnl,
                charmPnl = charmPnl,
                crossGammaPnl = crossGammaPnl,
                unexplainedPnl = unexplainedPnl,
            )
        }

        val totalPnl = positionAttributions.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.totalPnl, mc) }
        val deltaPnl = positionAttributions.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.deltaPnl, mc) }
        val gammaPnl = positionAttributions.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.gammaPnl, mc) }
        val vegaPnl = positionAttributions.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.vegaPnl, mc) }
        val thetaPnl = positionAttributions.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.thetaPnl, mc) }
        val rhoPnl = positionAttributions.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.rhoPnl, mc) }
        val vannaPnl = positionAttributions.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.vannaPnl, mc) }
        val volgaPnl = positionAttributions.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.volgaPnl, mc) }
        val charmPnl = positionAttributions.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.charmPnl, mc) }
        val crossGammaPnl = positionAttributions.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.crossGammaPnl, mc) }
        val unexplainedPnl = positionAttributions.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.unexplainedPnl, mc) }

        val dataQualityFlag = deriveDataQuality(positions)

        return PnlAttribution(
            bookId = bookId,
            date = date,
            currency = currency,
            totalPnl = totalPnl,
            deltaPnl = deltaPnl,
            gammaPnl = gammaPnl,
            vegaPnl = vegaPnl,
            thetaPnl = thetaPnl,
            rhoPnl = rhoPnl,
            vannaPnl = vannaPnl,
            volgaPnl = volgaPnl,
            charmPnl = charmPnl,
            crossGammaPnl = crossGammaPnl,
            unexplainedPnl = unexplainedPnl,
            positionAttributions = positionAttributions,
            dataQualityFlag = dataQualityFlag,
            calculatedAt = Instant.now(),
        )
    }

    /**
     * Computes cross-gamma P&L for each position based on pairwise correlation with other instruments.
     *
     * For each pair (i, j) with different instrument IDs:
     *   cross_gamma_ij = corr(i,j) * gamma_i * dS_i * gamma_j * dS_j
     *
     * Each pairwise term is split evenly: half assigned to position i, half to position j.
     * If no correlation data is available, returns empty map (all zero).
     */
    private fun computeCrossGamma(
        positions: List<PositionPnlInput>,
        correlations: Map<Pair<String, String>, Double>,
    ): Map<String, BigDecimal> {
        if (correlations.isEmpty() || positions.size < 2) return emptyMap()

        val result = mutableMapOf<String, BigDecimal>()

        for (i in positions.indices) {
            for (j in i + 1 until positions.size) {
                val pi = positions[i]
                val pj = positions[j]
                if (pi.instrumentId == pj.instrumentId) continue

                val corr = correlations[pi.instrumentId.value to pj.instrumentId.value] ?: continue
                if (corr == 0.0) continue

                // cross_gamma_ij = corr(i,j) * gamma_i * dS_i * gamma_j * dS_j
                val term = BigDecimal.valueOf(corr)
                    .multiply(pi.gamma, mc)
                    .multiply(pi.priceChange, mc)
                    .multiply(pj.gamma, mc)
                    .multiply(pj.priceChange, mc)

                // Split evenly between the two positions
                val halfTerm = term.multiply(half, mc)
                result[pi.instrumentId.value] = (result[pi.instrumentId.value] ?: BigDecimal.ZERO).add(halfTerm, mc)
                result[pj.instrumentId.value] = (result[pj.instrumentId.value] ?: BigDecimal.ZERO).add(halfTerm, mc)
            }
        }

        return result
    }

    /**
     * Derives the data quality flag from the set of positions.
     *
     * FULL_ATTRIBUTION: at least one position carries non-zero vanna or volga or charm —
     * meaning pricing Greeks were available and cross-Greek terms have been computed.
     *
     * PRICE_ONLY: every position has zero vanna, volga, and charm — no cross-Greek snapshot
     * was available, so the attribution only covers first-order terms.
     *
     * STALE_GREEKS is set externally by the calling service when it detects the SOD snapshot
     * was locked too long before market open; this method never returns it.
     */
    private fun deriveDataQuality(positions: List<PositionPnlInput>): AttributionDataQuality {
        val hasCrossGreeks = positions.any { pos ->
            pos.vanna.compareTo(BigDecimal.ZERO) != 0 ||
                pos.volga.compareTo(BigDecimal.ZERO) != 0 ||
                pos.charm.compareTo(BigDecimal.ZERO) != 0
        }
        return if (hasCrossGreeks) AttributionDataQuality.FULL_ATTRIBUTION else AttributionDataQuality.PRICE_ONLY
    }
}
