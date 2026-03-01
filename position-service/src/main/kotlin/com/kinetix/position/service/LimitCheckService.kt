package com.kinetix.position.service

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.Money
import com.kinetix.common.model.Position
import com.kinetix.common.model.Side
import com.kinetix.position.model.LimitBreach
import com.kinetix.position.model.LimitBreachResult
import com.kinetix.position.model.LimitBreachSeverity
import com.kinetix.position.model.TradeLimits
import com.kinetix.position.persistence.PositionRepository
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.util.Currency

class LimitCheckService(
    private val positionRepository: PositionRepository,
    private val defaultLimits: TradeLimits,
) {
    private val logger = LoggerFactory.getLogger(LimitCheckService::class.java)

    suspend fun check(command: BookTradeCommand): LimitBreachResult {
        val breaches = mutableListOf<LimitBreach>()

        val currentPosition = positionRepository.findByKey(command.portfolioId, command.instrumentId)
        val currentQuantity = currentPosition?.quantity ?: BigDecimal.ZERO

        val signedTradeQty = when (command.side) {
            Side.BUY -> command.quantity
            Side.SELL -> -command.quantity
        }
        val newQuantity = currentQuantity + signedTradeQty

        checkPositionLimit(newQuantity, breaches)

        val portfolioPositions = positionRepository.findByPortfolioId(command.portfolioId)
        val currentPortfolioValue = portfolioPositions.fold(BigDecimal.ZERO) { acc, pos ->
            acc + pos.marketValue.amount.abs()
        }
        val tradeNotional = command.price.amount * command.quantity
        val newPortfolioValue = currentPortfolioValue + tradeNotional

        checkNotionalLimit(newPortfolioValue, command.price.currency, breaches)
        checkConcentrationLimit(
            currentPosition = currentPosition,
            signedTradeQty = signedTradeQty,
            newPortfolioValue = newPortfolioValue,
            breaches = breaches,
        )

        val result = LimitBreachResult(breaches)
        if (result.blocked) {
            logger.warn("Limit check BLOCKED trade: portfolio={}, instrument={}, breaches={}",
                command.portfolioId.value, command.instrumentId.value,
                breaches.map { "${it.limitType}:${it.severity}" })
        } else if (breaches.isNotEmpty()) {
            logger.info("Limit check passed with warnings: portfolio={}, instrument={}, breaches={}",
                command.portfolioId.value, command.instrumentId.value,
                breaches.map { "${it.limitType}:${it.severity}" })
        } else {
            logger.debug("Limit check passed: portfolio={}, instrument={}",
                command.portfolioId.value, command.instrumentId.value)
        }
        return result
    }

    private fun checkPositionLimit(newQuantity: BigDecimal, breaches: MutableList<LimitBreach>) {
        val limit = defaultLimits.positionLimit ?: return
        val absNewQuantity = newQuantity.abs()
        val softThreshold = limit.multiply(BigDecimal.valueOf(defaultLimits.softLimitPct))

        if (absNewQuantity > limit) {
            breaches.add(
                LimitBreach(
                    limitType = "POSITION",
                    severity = LimitBreachSeverity.HARD,
                    currentValue = absNewQuantity.toPlainString(),
                    limitValue = limit.toPlainString(),
                    message = "Position quantity $absNewQuantity exceeds limit of $limit",
                )
            )
        } else if (absNewQuantity.compareTo(softThreshold) > 0) {
            breaches.add(
                LimitBreach(
                    limitType = "POSITION",
                    severity = LimitBreachSeverity.SOFT,
                    currentValue = absNewQuantity.toPlainString(),
                    limitValue = limit.toPlainString(),
                    message = "Position quantity $absNewQuantity approaching limit of $limit (${(defaultLimits.softLimitPct * 100).toInt()}% threshold)",
                )
            )
        }
    }

    private fun checkNotionalLimit(
        newPortfolioValue: BigDecimal,
        currency: Currency,
        breaches: MutableList<LimitBreach>,
    ) {
        val limit = defaultLimits.notionalLimit ?: return
        if (newPortfolioValue > limit.amount) {
            breaches.add(
                LimitBreach(
                    limitType = "NOTIONAL",
                    severity = LimitBreachSeverity.HARD,
                    currentValue = newPortfolioValue.toPlainString(),
                    limitValue = limit.amount.toPlainString(),
                    message = "Portfolio notional exposure $newPortfolioValue exceeds limit of ${limit.amount}",
                )
            )
        }
    }

    private fun checkConcentrationLimit(
        currentPosition: Position?,
        signedTradeQty: BigDecimal,
        newPortfolioValue: BigDecimal,
        breaches: MutableList<LimitBreach>,
    ) {
        val limitPct = defaultLimits.concentrationLimitPct ?: return
        if (newPortfolioValue.signum() == 0) return

        val currentMarketPrice = currentPosition?.marketPrice?.amount ?: BigDecimal.ZERO
        val currentQty = currentPosition?.quantity ?: BigDecimal.ZERO
        val newInstrumentQty = currentQty + signedTradeQty
        val instrumentValue = currentMarketPrice * newInstrumentQty.abs()
        val concentrationPct = instrumentValue.toDouble() / newPortfolioValue.toDouble()

        if (concentrationPct > limitPct) {
            breaches.add(
                LimitBreach(
                    limitType = "CONCENTRATION",
                    severity = LimitBreachSeverity.HARD,
                    currentValue = "%.1f%%".format(concentrationPct * 100),
                    limitValue = "%.1f%%".format(limitPct * 100),
                    message = "Instrument concentration %.1f%% exceeds limit of %.1f%%".format(
                        concentrationPct * 100,
                        limitPct * 100,
                    ),
                )
            )
        }
    }
}
