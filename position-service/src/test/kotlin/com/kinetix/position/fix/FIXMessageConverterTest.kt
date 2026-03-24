package com.kinetix.position.fix

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import java.math.BigDecimal

class FIXMessageConverterTest : FunSpec({

    val converter = FIXMessageConverter()

    test("parses a full-fill ExecutionReport (ExecType=F) into an InboundFillEvent") {
        // A minimal FIX 4.2 ExecutionReport represented as tag=value pairs
        val message = buildFIXMessage(
            "35" to "8",      // MsgType = ExecutionReport
            "49" to "BROKER", // SenderCompID
            "56" to "CLIENT", // TargetCompID
            "11" to "clord-001",   // ClOrdID
            "17" to "exec-001",    // ExecID
            "37" to "ord-001",     // OrderID
            "150" to "F",          // ExecType = FILL
            "32" to "100",         // LastQty
            "31" to "149.95",      // LastPx
            "14" to "100",         // CumQty
            "6" to "149.95",       // AvgPx
            "30" to "NYSE",        // LastMkt (venue)
        )
        val event = converter.parseExecutionReport(message)
        event.shouldNotBeNull()
        event.execId shouldBe "exec-001"
        event.orderId shouldBe "ord-001"
        event.execType shouldBe "F"
        event.lastQty.compareTo(BigDecimal("100")) shouldBe 0
        event.lastPrice.compareTo(BigDecimal("149.95")) shouldBe 0
        event.cumulativeQty.compareTo(BigDecimal("100")) shouldBe 0
        event.averagePrice.compareTo(BigDecimal("149.95")) shouldBe 0
        event.venue shouldBe "NYSE"
    }

    test("parses a partial-fill ExecutionReport (ExecType=1) correctly") {
        val message = buildFIXMessage(
            "35" to "8",
            "11" to "clord-002",
            "17" to "exec-002",
            "37" to "ord-002",
            "150" to "1",     // ExecType = PARTIAL_FILL
            "32" to "40",
            "31" to "150.00",
            "14" to "40",
            "6" to "150.00",
            "30" to "XNYS",
        )
        val event = converter.parseExecutionReport(message)
        event.shouldNotBeNull()
        event.execType shouldBe "1"
        event.lastQty.compareTo(BigDecimal("40")) shouldBe 0
        event.cumulativeQty.compareTo(BigDecimal("40")) shouldBe 0
    }

    test("parses a cancel ExecutionReport (ExecType=4)") {
        val message = buildFIXMessage(
            "35" to "8",
            "11" to "clord-003",
            "17" to "exec-003",
            "37" to "ord-003",
            "150" to "4",     // ExecType = CANCELLED
            "32" to "0",
            "31" to "0",
            "14" to "50",
            "6" to "149.90",
        )
        val event = converter.parseExecutionReport(message)
        event.shouldNotBeNull()
        event.execType shouldBe "4"
    }

    test("returns null for a non-ExecutionReport message type") {
        val message = buildFIXMessage(
            "35" to "D",  // NewOrderSingle, not an ExecutionReport
            "11" to "clord-004",
        )
        converter.parseExecutionReport(message).shouldBeNull()
    }

    test("rejects a fill with zero last quantity") {
        val message = buildFIXMessage(
            "35" to "8",
            "11" to "clord-005",
            "17" to "exec-005",
            "37" to "ord-005",
            "150" to "F",
            "32" to "0",   // zero quantity — invalid
            "31" to "150.00",
            "14" to "0",
            "6" to "150.00",
        )
        converter.parseExecutionReport(message).shouldBeNull()
    }

    test("rejects a fill with negative last price") {
        val message = buildFIXMessage(
            "35" to "8",
            "11" to "clord-006",
            "17" to "exec-006",
            "37" to "ord-006",
            "150" to "F",
            "32" to "100",
            "31" to "-1.00",  // negative price — invalid
            "14" to "100",
            "6" to "150.00",
        )
        converter.parseExecutionReport(message).shouldBeNull()
    }

    test("venue is null when LastMkt tag 30 is absent") {
        val message = buildFIXMessage(
            "35" to "8",
            "11" to "clord-007",
            "17" to "exec-007",
            "37" to "ord-007",
            "150" to "F",
            "32" to "100",
            "31" to "150.00",
            "14" to "100",
            "6" to "150.00",
        )
        val event = converter.parseExecutionReport(message)
        event.shouldNotBeNull()
        event.venue.shouldBeNull()
    }

    test("generates deterministic fill_id from session_id and exec_id") {
        val id1 = FIXMessageConverter.deterministicFillId("SESSION-A", "EXEC-001")
        val id2 = FIXMessageConverter.deterministicFillId("SESSION-A", "EXEC-001")
        val id3 = FIXMessageConverter.deterministicFillId("SESSION-A", "EXEC-002")
        id1 shouldBe id2
        (id1 == id3) shouldBe false
    }

    test("deterministic fill_id differs when session_id differs") {
        val id1 = FIXMessageConverter.deterministicFillId("SESSION-A", "EXEC-001")
        val id2 = FIXMessageConverter.deterministicFillId("SESSION-B", "EXEC-001")
        (id1 == id2) shouldBe false
    }
})

/**
 * Builds a pipe-delimited FIX-style tag=value string for testing.
 * Uses ASCII 0x01 (SOH) as the standard field delimiter is simulated via '|'.
 */
private fun buildFIXMessage(vararg pairs: Pair<String, String>): String =
    pairs.joinToString("|") { (tag, value) -> "$tag=$value" }
