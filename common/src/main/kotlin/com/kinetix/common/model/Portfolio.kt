package com.kinetix.common.model

import java.util.Currency

data class Portfolio(
    val id: PortfolioId,
    val name: String,
    val positions: Map<InstrumentId, Position> = emptyMap(),
) {
    init {
        require(name.isNotBlank()) { "Portfolio name must not be blank" }
    }

    fun totalMarketValue(currency: Currency): Money =
        positions.values
            .filter { it.currency == currency }
            .fold(Money.zero(currency)) { acc, pos -> acc + pos.marketValue }

    fun totalUnrealizedPnl(currency: Currency): Money =
        positions.values
            .filter { it.currency == currency }
            .fold(Money.zero(currency)) { acc, pos -> acc + pos.unrealizedPnl }

    fun bookTrade(trade: Trade): Portfolio {
        require(trade.portfolioId == id) { "Trade portfolioId does not match portfolio" }

        val existing = positions[trade.instrumentId]
            ?: Position.empty(
                portfolioId = id,
                instrumentId = trade.instrumentId,
                assetClass = trade.assetClass,
                currency = trade.price.currency,
            )

        val updated = existing.applyTrade(trade)
        return copy(positions = positions + (trade.instrumentId to updated))
    }

    fun markToMarket(instrumentId: InstrumentId, newMarketPrice: Money): Portfolio {
        val existing = positions[instrumentId] ?: return this
        val updated = existing.markToMarket(newMarketPrice)
        return copy(positions = positions + (instrumentId to updated))
    }
}
