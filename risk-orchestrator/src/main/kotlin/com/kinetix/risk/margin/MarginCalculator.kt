package com.kinetix.risk.margin

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.Position
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency

class MarginCalculator(
    private val marginRates: Map<AssetClass, BigDecimal> = DEFAULT_MARGIN_RATES,
) {
    fun calculate(
        positions: List<Position>,
        previousMTM: BigDecimal? = null,
    ): MarginEstimate {
        if (positions.isEmpty()) {
            return MarginEstimate(
                initialMargin = BigDecimal.ZERO,
                variationMargin = BigDecimal.ZERO,
                totalMargin = BigDecimal.ZERO,
                currency = Currency.getInstance("USD"),
            )
        }

        val currency = positions.first().currency

        val initialMargin = positions.fold(BigDecimal.ZERO) { acc, position ->
            val marketValue = position.marketPrice.amount.multiply(position.quantity).abs()
            val rate = marginRates[position.assetClass] ?: DEFAULT_RATE
            acc + marketValue.multiply(rate)
        }.setScale(2, RoundingMode.HALF_UP)

        val currentMTM = positions.fold(BigDecimal.ZERO) { acc, position ->
            acc + position.marketPrice.amount.multiply(position.quantity)
        }

        val variationMargin = if (previousMTM != null) {
            currentMTM.subtract(previousMTM).abs().setScale(2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        return MarginEstimate(
            initialMargin = initialMargin,
            variationMargin = variationMargin,
            totalMargin = initialMargin.add(variationMargin),
            currency = currency,
        )
    }

    companion object {
        private val DEFAULT_RATE = BigDecimal("0.10")
        private val DEFAULT_MARGIN_RATES = mapOf(
            AssetClass.EQUITY to BigDecimal("0.15"),
            AssetClass.FIXED_INCOME to BigDecimal("0.05"),
            AssetClass.FX to BigDecimal("0.03"),
            AssetClass.COMMODITY to BigDecimal("0.20"),
            AssetClass.DERIVATIVE to BigDecimal("0.25"),
        )
    }
}
