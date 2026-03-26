package com.kinetix.regulatory.historical

import com.kinetix.regulatory.client.PriceServiceClient
import com.kinetix.regulatory.client.RiskOrchestratorClient
import com.kinetix.regulatory.historical.dto.ReplayRequest
import com.kinetix.regulatory.historical.dto.ReplayResultResponse
import kotlin.math.ln

/**
 * Runs a historical scenario replay for a given period against a live book.
 *
 * The service loads the period metadata and its stored daily returns from the
 * repository, then delegates the actual calculation to the risk-orchestrator,
 * which proxies to the risk-engine gRPC [StressTestService.RunHistoricalReplay].
 *
 * Responsibility split:
 *   - regulatory-service owns period metadata, governance, and stored returns
 *   - risk-orchestrator owns position loading and gRPC communication
 */
class HistoricalReplayService(
    private val repository: HistoricalScenarioRepository,
    private val riskOrchestratorClient: RiskOrchestratorClient,
    private val priceServiceClient: PriceServiceClient? = null,
) {

    suspend fun runReplay(periodId: String, request: ReplayRequest): ReplayResultResponse {
        val period = repository.findPeriodById(periodId)
            ?: throw NoSuchElementException("Historical scenario period not found: $periodId")

        val allReturns = repository.findAllReturns(periodId)
        val instrumentReturns = groupReturnsByInstrument(allReturns)

        val result = riskOrchestratorClient.runHistoricalReplay(
            bookId = request.bookId,
            instrumentReturns = instrumentReturns,
            windowStart = period.startDate,
            windowEnd = period.endDate,
        )

        return ReplayResultResponse(
            periodId = periodId,
            scenarioName = result.scenarioName,
            bookId = request.bookId,
            totalPnlImpact = result.totalPnlImpact,
            positionImpacts = result.positionImpacts.map { impact ->
                com.kinetix.regulatory.historical.dto.PositionReplayImpact(
                    instrumentId = impact.instrumentId,
                    assetClass = impact.assetClass,
                    marketValue = impact.marketValue,
                    pnlImpact = impact.pnlImpact,
                    dailyPnl = impact.dailyPnl,
                    proxyUsed = impact.proxyUsed,
                )
            },
            windowStart = result.windowStart,
            windowEnd = result.windowEnd,
            calculatedAt = result.calculatedAt,
        )
    }

    /**
     * Replays an arbitrary date range by fetching daily close prices from the price-service
     * and computing log returns on-the-fly. Instruments with insufficient price history
     * (fewer than two data points) are silently excluded — the risk-orchestrator handles
     * missing instruments via proxy returns.
     *
     * Requires [priceServiceClient] to be configured.
     */
    suspend fun replayCustomDateRange(
        bookId: String,
        instrumentIds: List<String>,
        startDate: String,
        endDate: String,
    ): ReplayResultResponse {
        require(startDate < endDate) {
            "startDate must be before endDate: startDate=$startDate endDate=$endDate"
        }
        val client = priceServiceClient
            ?: throw IllegalStateException("PriceServiceClient not configured")

        val fromIso = "${startDate}T00:00:00Z"
        val toIso = "${endDate}T00:00:00Z"

        val instrumentReturns = instrumentIds.mapNotNull { instrumentId ->
            val prices = client.fetchDailyClosePrices(instrumentId, fromIso, toIso)
            val returns = computeLogReturns(prices)
            if (returns.isEmpty()) null else instrumentId to returns
        }.toMap()

        return riskOrchestratorClient.runHistoricalReplay(
            bookId = bookId,
            instrumentReturns = instrumentReturns,
            windowStart = startDate,
            windowEnd = endDate,
        )
    }

    /**
     * Groups a flat list of daily returns into a map of instrumentId -> ordered return values.
     * The returns are already ordered by returnDate from the repository query.
     */
    private fun groupReturnsByInstrument(returns: List<HistoricalScenarioReturn>): Map<String, List<Double>> =
        returns
            .groupBy { it.instrumentId }
            .mapValues { (_, rows) -> rows.map { it.dailyReturn.toDouble() } }

    /**
     * Derives log returns from a sequence of daily close prices.
     * Returns are ln(p[t] / p[t-1]) for consecutive prices.
     * Returns an empty list if fewer than two prices are available.
     */
    private fun computeLogReturns(prices: List<DailyClosePrice>): List<Double> {
        if (prices.size < 2) return emptyList()
        return prices.zipWithNext { prev, curr -> ln(curr.closePrice / prev.closePrice) }
    }
}
