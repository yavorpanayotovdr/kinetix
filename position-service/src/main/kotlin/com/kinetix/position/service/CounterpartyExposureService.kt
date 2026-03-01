package com.kinetix.position.service

import com.kinetix.common.model.PortfolioId
import com.kinetix.common.model.Side
import com.kinetix.common.model.Trade
import com.kinetix.position.model.CounterpartyExposure
import com.kinetix.position.persistence.TradeEventRepository
import java.math.BigDecimal
import java.math.RoundingMode

class CounterpartyExposureService(
    private val tradeEventRepository: TradeEventRepository,
) {
    suspend fun getExposures(portfolioId: PortfolioId): List<CounterpartyExposure> {
        val trades = tradeEventRepository.findByPortfolioId(portfolioId)

        return trades
            .filter { it.counterpartyId != null }
            .groupBy { it.counterpartyId!! }
            .map { (counterpartyId, counterpartyTrades) ->
                val netExposure = counterpartyTrades.fold(BigDecimal.ZERO) { acc, trade ->
                    val notional = trade.notional.amount
                    when (trade.side) {
                        Side.BUY -> acc + notional
                        Side.SELL -> acc - notional
                    }
                }.setScale(2, RoundingMode.HALF_UP)

                val grossExposure = counterpartyTrades.fold(BigDecimal.ZERO) { acc, trade ->
                    acc + trade.notional.amount
                }.setScale(2, RoundingMode.HALF_UP)

                CounterpartyExposure(
                    counterpartyId = counterpartyId,
                    netExposure = netExposure,
                    grossExposure = grossExposure,
                    positionCount = counterpartyTrades.size,
                )
            }
    }
}
