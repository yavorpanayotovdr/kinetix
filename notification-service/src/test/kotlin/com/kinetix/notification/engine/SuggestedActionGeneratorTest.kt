package com.kinetix.notification.engine

import com.kinetix.common.kafka.events.PositionBreakdownItem
import com.kinetix.common.kafka.events.RiskResultEvent
import com.kinetix.notification.model.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class SuggestedActionGeneratorTest : FunSpec({

    val generator = SuggestedActionGenerator()

    fun varBreachRule(threshold: Double = 100_000.0) = AlertRule(
        id = "r1", name = "VaR Limit", type = AlertType.VAR_BREACH,
        threshold = threshold, operator = ComparisonOperator.GREATER_THAN,
        severity = Severity.CRITICAL, channels = listOf(DeliveryChannel.IN_APP),
    )

    fun riskEvent(varValue: String = "150000.0") = RiskResultEvent(
        portfolioId = "book-1",
        varValue = varValue,
        expectedShortfall = "180000.0",
        calculationType = "PARAMETRIC",
        calculatedAt = "2025-01-15T10:00:00Z",
    )

    fun linearContributor(id: String = "AAPL", varContribution: String = "50000.0", pctOfTotal: String = "38.0") =
        PositionBreakdownItem(
            instrumentId = id,
            assetClass = "EQUITY",
            marketValue = "155000.0",
            varContribution = varContribution,
            percentageOfTotal = pctOfTotal,
            quantity = "100",
        )

    fun optionContributor(id: String = "AAPL_CALL", gamma: String = "2.5", vega: String = "45.0") =
        PositionBreakdownItem(
            instrumentId = id,
            assetClass = "EQUITY",
            marketValue = "10000.0",
            varContribution = "30000.0",
            percentageOfTotal = "25.0",
            delta = "0.6",
            gamma = gamma,
            vega = vega,
            quantity = "50",
        )

    test("generates linear reduction estimate for equity position") {
        val suggestion = generator.generate(varBreachRule(), riskEvent(), listOf(linearContributor()))
        suggestion shouldContain "AAPL"
        suggestion shouldContain "38%"
        suggestion shouldContain "Reducing AAPL by"
    }

    test("shows What-If guidance for option positions with significant gamma") {
        val suggestion = generator.generate(varBreachRule(), riskEvent(), listOf(optionContributor()))
        suggestion shouldContain "Non-linear position"
        suggestion shouldContain "What-If"
        suggestion shouldNotContain "Reducing"
    }

    test("returns null for non-VaR alert types") {
        val pnlRule = AlertRule(
            id = "r2", name = "PnL", type = AlertType.PNL_THRESHOLD,
            threshold = 100_000.0, operator = ComparisonOperator.GREATER_THAN,
            severity = Severity.WARNING, channels = listOf(DeliveryChannel.IN_APP),
        )
        val suggestion = generator.generate(pnlRule, riskEvent(), listOf(linearContributor()))
        suggestion shouldBe null
    }

    test("returns null when no contributors provided") {
        val suggestion = generator.generate(varBreachRule(), riskEvent(), emptyList())
        suggestion shouldBe null
    }

    test("returns null when VaR is below threshold") {
        val suggestion = generator.generate(varBreachRule(), riskEvent(varValue = "50000.0"), listOf(linearContributor()))
        suggestion shouldBe null
    }

    test("generates suggestion for RISK_LIMIT type") {
        val riskRule = AlertRule(
            id = "r3", name = "Risk Limit", type = AlertType.RISK_LIMIT,
            threshold = 100_000.0, operator = ComparisonOperator.GREATER_THAN,
            severity = Severity.CRITICAL, channels = listOf(DeliveryChannel.IN_APP),
        )
        val suggestion = generator.generate(riskRule, riskEvent(), listOf(linearContributor()))
        suggestion shouldContain "AAPL"
        suggestion shouldContain "Reducing"
    }
})
