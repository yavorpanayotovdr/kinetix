package com.kinetix.gateway.websocket

import com.kinetix.common.kafka.events.IntradayPnlEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PnlUpdateMappingTest : FunSpec({

    test("PnlUpdate.from maps cross-Greek fields from IntradayPnlEvent") {
        val event = IntradayPnlEvent(
            bookId = "book-1",
            snapshotAt = "2026-03-24T10:00:00Z",
            baseCurrency = "USD",
            trigger = "position_change",
            totalPnl = "1000.00",
            realisedPnl = "400.00",
            unrealisedPnl = "600.00",
            deltaPnl = "800.00",
            gammaPnl = "50.00",
            vegaPnl = "30.00",
            thetaPnl = "-10.00",
            rhoPnl = "5.00",
            vannaPnl = "12.50",
            volgaPnl = "7.30",
            charmPnl = "-3.10",
            crossGammaPnl = "5.00",
            unexplainedPnl = "100.20",
            highWaterMark = "1200.00",
            correlationId = "corr-1",
        )

        val update = PnlUpdate.from(event)

        update.vannaPnl shouldBe "12.50"
        update.volgaPnl shouldBe "7.30"
        update.charmPnl shouldBe "-3.10"
        update.crossGammaPnl shouldBe "5.00"
    }

    test("PnlUpdate.from maps cross-Greek fields as zero when event carries defaults") {
        val event = IntradayPnlEvent(
            bookId = "book-2",
            snapshotAt = "2026-03-24T11:00:00Z",
            baseCurrency = "EUR",
            trigger = "trade_booked",
            totalPnl = "500.00",
            realisedPnl = "200.00",
            unrealisedPnl = "300.00",
            deltaPnl = "400.00",
            gammaPnl = "40.00",
            vegaPnl = "20.00",
            thetaPnl = "-5.00",
            rhoPnl = "3.00",
            unexplainedPnl = "42.00",
            highWaterMark = "600.00",
        )

        val update = PnlUpdate.from(event)

        update.vannaPnl shouldBe "0"
        update.volgaPnl shouldBe "0"
        update.charmPnl shouldBe "0"
        update.crossGammaPnl shouldBe "0"
    }

    test("PnlUpdate.from maps all standard fields correctly alongside cross-Greeks") {
        val event = IntradayPnlEvent(
            bookId = "book-3",
            snapshotAt = "2026-03-24T12:00:00Z",
            baseCurrency = "GBP",
            trigger = "price_update",
            totalPnl = "2000.00",
            realisedPnl = "800.00",
            unrealisedPnl = "1200.00",
            deltaPnl = "1500.00",
            gammaPnl = "100.00",
            vegaPnl = "60.00",
            thetaPnl = "-20.00",
            rhoPnl = "10.00",
            vannaPnl = "25.00",
            volgaPnl = "15.00",
            charmPnl = "-6.00",
            crossGammaPnl = "10.00",
            unexplainedPnl = "306.00",
            highWaterMark = "2500.00",
            correlationId = "corr-xyz",
        )

        val update = PnlUpdate.from(event)

        update.type shouldBe "pnl"
        update.bookId shouldBe "book-3"
        update.baseCurrency shouldBe "GBP"
        update.trigger shouldBe "price_update"
        update.totalPnl shouldBe "2000.00"
        update.deltaPnl shouldBe "1500.00"
        update.vannaPnl shouldBe "25.00"
        update.volgaPnl shouldBe "15.00"
        update.charmPnl shouldBe "-6.00"
        update.crossGammaPnl shouldBe "10.00"
        update.correlationId shouldBe "corr-xyz"
    }
})
