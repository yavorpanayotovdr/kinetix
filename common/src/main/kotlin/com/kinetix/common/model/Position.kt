package com.kinetix.common.model

import java.math.BigDecimal
import java.math.MathContext
import java.util.Currency

data class Position(
    val portfolioId: PortfolioId,
    val instrumentId: InstrumentId,
    val assetClass: AssetClass,
    val quantity: BigDecimal,
    val averageCost: Money,
    val marketPrice: Money,
) {
    init {
        require(averageCost.currency == marketPrice.currency) {
            "Currency mismatch: averageCost=${averageCost.currency}, marketPrice=${marketPrice.currency}"
        }
    }

    val currency: Currency
        get() = marketPrice.currency

    val marketValue: Money
        get() = marketPrice * quantity

    val unrealizedPnl: Money
        get() = (marketPrice - averageCost) * quantity

    fun markToMarket(newMarketPrice: Money): Position {
        require(newMarketPrice.currency == currency) {
            "Cannot mark to market with different currency: expected $currency, got ${newMarketPrice.currency}"
        }
        return copy(marketPrice = newMarketPrice)
    }

    fun applyTrade(trade: Trade): Position {
        require(trade.portfolioId == portfolioId) { "Trade portfolioId mismatch" }
        require(trade.instrumentId == instrumentId) { "Trade instrumentId mismatch" }
        require(trade.price.currency == currency) { "Trade currency mismatch" }

        val tradeSignedQty = trade.signedQuantity
        val newQuantity = quantity + tradeSignedQty

        val newAverageCost = when {
            quantity.signum() == 0 -> trade.price

            quantity.signum() == tradeSignedQty.signum() -> {
                val totalCost = averageCost.amount * quantity.abs() + trade.price.amount * trade.quantity
                val totalQty = quantity.abs() + trade.quantity
                Money(totalCost.divide(totalQty, MathContext.DECIMAL128), currency)
            }

            newQuantity.signum() == quantity.signum() -> averageCost

            newQuantity.signum() != 0 -> trade.price

            else -> averageCost
        }

        return copy(
            quantity = newQuantity,
            averageCost = newAverageCost,
        )
    }

    companion object {
        fun empty(
            portfolioId: PortfolioId,
            instrumentId: InstrumentId,
            assetClass: AssetClass,
            currency: Currency,
        ): Position = Position(
            portfolioId = portfolioId,
            instrumentId = instrumentId,
            assetClass = assetClass,
            quantity = BigDecimal.ZERO,
            averageCost = Money.zero(currency),
            marketPrice = Money.zero(currency),
        )
    }
}
