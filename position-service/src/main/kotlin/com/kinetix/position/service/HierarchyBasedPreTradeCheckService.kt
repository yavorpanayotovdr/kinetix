package com.kinetix.position.service

import com.kinetix.common.model.Side
import com.kinetix.position.client.InstrumentLiquidityClient
import com.kinetix.position.model.LimitBreach
import com.kinetix.position.model.LimitBreachResult
import com.kinetix.position.model.LimitBreachSeverity
import com.kinetix.position.model.LimitCheckStatus
import com.kinetix.position.model.LimitLevel
import com.kinetix.position.model.LimitType
import com.kinetix.position.persistence.PositionRepository
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode

private val ADV_HARD_THRESHOLD = BigDecimal("0.10")
private val ADV_SOFT_THRESHOLD = BigDecimal("0.05")

class HierarchyBasedPreTradeCheckService(
    private val positionRepository: PositionRepository,
    private val limitHierarchyService: LimitHierarchyService,
    private val liquidityClient: InstrumentLiquidityClient? = null,
) : PreTradeCheckService {

    private val logger = LoggerFactory.getLogger(HierarchyBasedPreTradeCheckService::class.java)

    override suspend fun check(command: BookTradeCommand): LimitBreachResult {
        val breaches = mutableListOf<LimitBreach>()
        val bookId = command.bookId.value

        val currentPosition = positionRepository.findByKey(command.bookId, command.instrumentId)
        val currentQuantity = currentPosition?.quantity ?: BigDecimal.ZERO
        val signedTradeQty = when (command.side) {
            Side.BUY -> command.quantity
            Side.SELL -> -command.quantity
        }
        val newQuantity = (currentQuantity + signedTradeQty).abs()

        val portfolioPositions = positionRepository.findByBookId(command.bookId)
        val currentPortfolioValue = portfolioPositions.fold(BigDecimal.ZERO) { acc, pos ->
            acc + pos.marketValue.amount.abs()
        }
        val tradeNotional = command.price.amount * command.quantity
        val newPortfolioValue = currentPortfolioValue + tradeNotional

        checkLimit(
            bookId = bookId,
            limitType = LimitType.POSITION,
            exposure = newQuantity,
            description = "Position quantity",
            breaches = breaches,
        )

        checkLimit(
            bookId = bookId,
            limitType = LimitType.NOTIONAL,
            exposure = newPortfolioValue,
            description = "Portfolio notional",
            breaches = breaches,
        )

        if (newPortfolioValue.signum() != 0) {
            val currentMarketPrice = currentPosition?.marketPrice?.amount ?: BigDecimal.ZERO
            val newInstrumentQty = currentQuantity + signedTradeQty
            val instrumentValue = currentMarketPrice * newInstrumentQty.abs()
            val concentrationPct = instrumentValue.divide(newPortfolioValue, 10, java.math.RoundingMode.HALF_UP)

            checkLimit(
                bookId = bookId,
                limitType = LimitType.CONCENTRATION,
                exposure = concentrationPct,
                description = "Instrument concentration",
                breaches = breaches,
            )
        }

        checkAdvConcentration(command, tradeNotional, breaches)

        val result = LimitBreachResult(breaches)
        if (result.blocked) {
            logger.warn(
                "Limit check BLOCKED trade: book={}, instrument={}, breaches={}",
                command.bookId.value, command.instrumentId.value,
                breaches.map { "${it.limitType}:${it.severity}" },
            )
        } else if (breaches.isNotEmpty()) {
            logger.info(
                "Limit check passed with warnings: book={}, instrument={}, breaches={}",
                command.bookId.value, command.instrumentId.value,
                breaches.map { "${it.limitType}:${it.severity}" },
            )
        } else {
            logger.debug(
                "Limit check passed: book={}, instrument={}",
                command.bookId.value, command.instrumentId.value,
            )
        }
        return result
    }

    private suspend fun checkAdvConcentration(
        command: BookTradeCommand,
        tradeNotional: BigDecimal,
        breaches: MutableList<LimitBreach>,
    ) {
        val client = liquidityClient ?: return

        val adv = client.getAdv(command.instrumentId)

        if (adv == null || adv.signum() == 0) {
            breaches.add(
                LimitBreach(
                    limitType = LimitType.ADV_CONCENTRATION.name,
                    severity = LimitBreachSeverity.HARD,
                    currentValue = tradeNotional.toPlainString(),
                    limitValue = "0",
                    message = "ADV data unavailable for ${command.instrumentId.value} — trade blocked (fail-safe)",
                )
            )
            return
        }

        val advPct = tradeNotional.divide(adv, 10, RoundingMode.HALF_UP)

        when {
            advPct > ADV_HARD_THRESHOLD -> breaches.add(
                LimitBreach(
                    limitType = LimitType.ADV_CONCENTRATION.name,
                    severity = LimitBreachSeverity.HARD,
                    currentValue = advPct.toPlainString(),
                    limitValue = ADV_HARD_THRESHOLD.toPlainString(),
                    message = "Trade notional is ${advPct.multiply(BigDecimal("100")).setScale(2, RoundingMode.HALF_UP)}% of ADV — exceeds hard limit of 10%",
                )
            )
            advPct > ADV_SOFT_THRESHOLD -> breaches.add(
                LimitBreach(
                    limitType = LimitType.ADV_CONCENTRATION.name,
                    severity = LimitBreachSeverity.SOFT,
                    currentValue = advPct.toPlainString(),
                    limitValue = ADV_SOFT_THRESHOLD.toPlainString(),
                    message = "Trade notional is ${advPct.multiply(BigDecimal("100")).setScale(2, RoundingMode.HALF_UP)}% of ADV — approaching limit of 10%",
                )
            )
            else -> { /* within acceptable ADV concentration */ }
        }
    }

    private suspend fun checkLimit(
        bookId: String,
        limitType: LimitType,
        exposure: BigDecimal,
        description: String,
        breaches: MutableList<LimitBreach>,
    ) {
        val checkResult = limitHierarchyService.checkLimit(
            entityId = bookId,
            level = LimitLevel.BOOK,
            limitType = limitType,
            currentExposure = exposure,
        )

        when (checkResult.status) {
            LimitCheckStatus.BREACHED -> breaches.add(
                LimitBreach(
                    limitType = limitType.name,
                    severity = LimitBreachSeverity.HARD,
                    currentValue = exposure.toPlainString(),
                    limitValue = (checkResult.effectiveLimit ?: checkResult.limitValue ?: BigDecimal.ZERO).toPlainString(),
                    message = checkResult.message
                        ?: "$description $exposure exceeds limit at ${checkResult.breachedAt ?: "BOOK"} level",
                )
            )
            LimitCheckStatus.WARNING -> breaches.add(
                LimitBreach(
                    limitType = limitType.name,
                    severity = LimitBreachSeverity.SOFT,
                    currentValue = exposure.toPlainString(),
                    limitValue = (checkResult.effectiveLimit ?: checkResult.limitValue ?: BigDecimal.ZERO).toPlainString(),
                    message = checkResult.message
                        ?: "$description $exposure approaching limit at ${checkResult.breachedAt ?: "BOOK"} level",
                )
            )
            LimitCheckStatus.OK -> { /* no breach */ }
        }
    }
}
