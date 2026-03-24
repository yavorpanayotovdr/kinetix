package com.kinetix.position.fix

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.Instant

private fun pbPos(instrumentId: String, qty: String, price: String) =
    PrimeBrokerPosition(instrumentId, BigDecimal(qty), BigDecimal(price))

class PrimeBrokerReconciliationServiceTest : FunSpec({

    val service = PrimeBrokerReconciliationService()
    val reconciledAt = Instant.parse("2026-03-24T18:00:00Z")

    test("all positions match prime broker — status is CLEAN with no breaks") {
        val internal = mapOf(
            "AAPL" to BigDecimal("100"),
            "GOOGL" to BigDecimal("50"),
        )
        val pbPositions = mapOf(
            "AAPL" to PrimeBrokerPosition("AAPL", BigDecimal("100"), BigDecimal("150.00")),
            "GOOGL" to PrimeBrokerPosition("GOOGL", BigDecimal("50"), BigDecimal("2800.00")),
        )
        val result = service.reconcile("book-1", "2026-03-24", internal, pbPositions, reconciledAt)
        result.status shouldBe "CLEAN"
        result.breakCount shouldBe 0
        result.breaks.shouldBeEmpty()
        result.matchedCount shouldBe 2
        result.totalPositions shouldBe 2
    }

    test("breaks smaller than 1 unit are auto-resolved and not included in breaks") {
        val internal = mapOf(
            "AAPL" to BigDecimal("100.0001"),  // rounding from partial fill
        )
        val pbPositions = mapOf(
            "AAPL" to PrimeBrokerPosition("AAPL", BigDecimal("100"), BigDecimal("150.00")),
        )
        val result = service.reconcile("book-1", "2026-03-24", internal, pbPositions, reconciledAt)
        result.status shouldBe "CLEAN"
        result.breakCount shouldBe 0
    }

    test("material break over 1 unit is included in result") {
        val internal = mapOf(
            "AAPL" to BigDecimal("105"),
        )
        val pbPositions = mapOf(
            "AAPL" to PrimeBrokerPosition("AAPL", BigDecimal("100"), BigDecimal("150.00")),
        )
        val result = service.reconcile("book-1", "2026-03-24", internal, pbPositions, reconciledAt)
        result.status shouldBe "BREAKS_FOUND"
        result.breakCount shouldBe 1
        result.breaks shouldHaveSize 1
        val br = result.breaks[0]
        br.instrumentId shouldBe "AAPL"
        br.internalQty.compareTo(BigDecimal("105")) shouldBe 0
        br.primeBrokerQty.compareTo(BigDecimal("100")) shouldBe 0
        br.breakQty.compareTo(BigDecimal("5")) shouldBe 0
    }

    test("break notional is computed as abs(break_qty) * prime_broker_price") {
        val internal = mapOf("AAPL" to BigDecimal("110"))
        val pbPositions = mapOf(
            "AAPL" to PrimeBrokerPosition("AAPL", BigDecimal("100"), BigDecimal("200.00")),
        )
        val result = service.reconcile("book-1", "2026-03-24", internal, pbPositions, reconciledAt)
        val br = result.breaks[0]
        // break_qty=10, price=200 → notional = 2000
        br.breakNotional.compareTo(BigDecimal("2000.00")) shouldBe 0
    }

    test("instrument present in internal but missing in prime broker creates a break") {
        val internal = mapOf("AAPL" to BigDecimal("100"))
        val pbPositions = emptyMap<String, PrimeBrokerPosition>()
        val result = service.reconcile("book-1", "2026-03-24", internal, pbPositions, reconciledAt)
        result.breakCount shouldBe 1
        result.breaks[0].primeBrokerQty.compareTo(BigDecimal.ZERO) shouldBe 0
    }

    test("instrument present in prime broker but missing in internal creates a break") {
        val internal = emptyMap<String, BigDecimal>()
        val pbPositions = mapOf(
            "MSFT" to PrimeBrokerPosition("MSFT", BigDecimal("50"), BigDecimal("300.00")),
        )
        val result = service.reconcile("book-1", "2026-03-24", internal, pbPositions, reconciledAt)
        result.breakCount shouldBe 1
        result.breaks[0].internalQty.compareTo(BigDecimal.ZERO) shouldBe 0
    }

    test("multiple breaks are all included in result") {
        val internal = mapOf(
            "AAPL" to BigDecimal("105"),
            "GOOGL" to BigDecimal("55"),
        )
        val pbPositions = mapOf(
            "AAPL" to PrimeBrokerPosition("AAPL", BigDecimal("100"), BigDecimal("150.00")),
            "GOOGL" to PrimeBrokerPosition("GOOGL", BigDecimal("50"), BigDecimal("2800.00")),
        )
        val result = service.reconcile("book-1", "2026-03-24", internal, pbPositions, reconciledAt)
        result.breakCount shouldBe 2
        result.breaks shouldHaveSize 2
    }

    test("break with notional above 10000 is assigned CRITICAL severity") {
        // 100 units at $200/unit = $20,000 notional > $10,000 threshold
        val internal = mapOf("AAPL" to BigDecimal("200"))
        val pbPositions = mapOf("AAPL" to pbPos("AAPL", "100", "200.00"))
        val result = service.reconcile("book-1", "2026-03-24", internal, pbPositions, reconciledAt)
        result.breaks[0].severity shouldBe ReconciliationBreakSeverity.CRITICAL
    }

    test("break with notional exactly at 10000 is assigned CRITICAL severity") {
        // 100 units at $100/unit = $10,000 = threshold → CRITICAL
        val internal = mapOf("AAPL" to BigDecimal("200"))
        val pbPositions = mapOf("AAPL" to pbPos("AAPL", "100", "100.00"))
        val result = service.reconcile("book-1", "2026-03-24", internal, pbPositions, reconciledAt)
        result.breaks[0].severity shouldBe ReconciliationBreakSeverity.CRITICAL
    }

    test("break with notional below 10000 is assigned NORMAL severity") {
        // 5 units at $100/unit = $500 notional < $10,000
        val internal = mapOf("AAPL" to BigDecimal("105"))
        val pbPositions = mapOf("AAPL" to pbPos("AAPL", "100", "100.00"))
        val result = service.reconcile("book-1", "2026-03-24", internal, pbPositions, reconciledAt)
        result.breaks[0].severity shouldBe ReconciliationBreakSeverity.NORMAL
    }

    test("multiple breaks have independent severity based on their own notional") {
        val internal = mapOf(
            "AAPL" to BigDecimal("200"),  // break=100 units @ $200 = $20,000 -> CRITICAL
            "MSFT" to BigDecimal("105"),  // break=5 units @ $10 = $50 -> NORMAL
        )
        val pbPositions = mapOf(
            "AAPL" to pbPos("AAPL", "100", "200.00"),
            "MSFT" to pbPos("MSFT", "100", "10.00"),
        )
        val result = service.reconcile("book-1", "2026-03-24", internal, pbPositions, reconciledAt)
        result.breaks shouldHaveSize 2
        val byId = result.breaks.associateBy { it.instrumentId }
        byId["AAPL"]!!.severity shouldBe ReconciliationBreakSeverity.CRITICAL
        byId["MSFT"]!!.severity shouldBe ReconciliationBreakSeverity.NORMAL
    }

    test("reconciliation metadata is correct") {
        val internal = mapOf("AAPL" to BigDecimal("100"))
        val pbPositions = mapOf(
            "AAPL" to PrimeBrokerPosition("AAPL", BigDecimal("100"), BigDecimal("150.00")),
        )
        val result = service.reconcile("book-1", "2026-03-24", internal, pbPositions, reconciledAt)
        result.bookId shouldBe "book-1"
        result.reconciliationDate shouldBe "2026-03-24"
        result.reconciledAt shouldBe reconciledAt
    }
})
