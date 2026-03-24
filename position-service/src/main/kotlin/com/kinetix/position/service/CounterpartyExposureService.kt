package com.kinetix.position.service

import com.kinetix.common.model.BookId
import com.kinetix.common.model.Side
import com.kinetix.common.model.Trade
import com.kinetix.position.model.CounterpartyExposure
import com.kinetix.position.model.NettingSetExposure
import com.kinetix.position.persistence.NettingSetTradeRepository
import com.kinetix.position.persistence.TradeEventRepository
import java.math.BigDecimal
import java.math.RoundingMode

class CounterpartyExposureService(
    private val tradeEventRepository: TradeEventRepository,
    private val nettingSetTradeRepository: NettingSetTradeRepository? = null,
) {
    suspend fun getExposures(bookId: BookId): List<CounterpartyExposure> {
        val trades = tradeEventRepository.findByBookId(bookId)
        val counterpartyTrades = trades.filter { it.counterpartyId != null }

        val nettingSets: Map<String, String> = if (nettingSetTradeRepository != null && counterpartyTrades.isNotEmpty()) {
            nettingSetTradeRepository.findNettingSetsByTradeIds(counterpartyTrades.map { it.tradeId.value })
        } else {
            emptyMap()
        }

        return counterpartyTrades
            .groupBy { it.counterpartyId!! }
            .map { (counterpartyId, cpTrades) ->
                buildExposure(counterpartyId, cpTrades, nettingSets)
            }
    }

    private fun buildExposure(
        counterpartyId: String,
        trades: List<Trade>,
        nettingSets: Map<String, String>,
    ): CounterpartyExposure {
        val grossExposure = trades.fold(BigDecimal.ZERO) { acc, trade ->
            acc + trade.notional.amount
        }.setScale(2, RoundingMode.HALF_UP)

        // Group by netting set. Trades with no netting set each form their own singleton group.
        // We use nettingSetId as key when present, or a trade-specific sentinel otherwise.
        val groups: Map<String?, List<Trade>> = trades.groupBy { trade ->
            nettingSets[trade.tradeId.value]
        }

        val breakdown = groups.map { (nettingSetId, groupTrades) ->
            val rawNet = groupTrades.fold(BigDecimal.ZERO) { acc, trade ->
                val notional = trade.notional.amount
                when (trade.side) {
                    Side.BUY -> acc + notional
                    Side.SELL -> acc - notional
                }
            }
            // Floor at zero: we can't have negative credit exposure within a netting set
            val netExposure = rawNet.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
            NettingSetExposure(
                nettingSetId = nettingSetId,
                netExposure = netExposure,
                positionCount = groupTrades.size,
            )
        }

        val netExposure = breakdown.fold(BigDecimal.ZERO) { acc, ns -> acc + ns.netExposure }

        return CounterpartyExposure(
            counterpartyId = counterpartyId,
            netExposure = netExposure,
            grossExposure = grossExposure,
            positionCount = trades.size,
            nettingSetBreakdown = breakdown,
        )
    }
}
