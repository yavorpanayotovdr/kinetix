package com.kinetix.schema

import com.kinetix.common.kafka.events.InstrumentPnlItem
import com.kinetix.common.kafka.events.IntradayPnlEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldHaveSize
import kotlinx.serialization.json.Json

class IntradayPnlEventSchemaCompatibilityTest : FunSpec({

    val json = Json { ignoreUnknownKeys = true }

    test("IntradayPnlEvent serializes and deserializes all required fields") {
        val event = IntradayPnlEvent(
            bookId = "book-1",
            snapshotAt = "2026-03-24T10:15:00Z",
            baseCurrency = "USD",
            trigger = "position_change",
            totalPnl = "12345.67",
            realisedPnl = "5000.00",
            unrealisedPnl = "7345.67",
            deltaPnl = "8000.00",
            gammaPnl = "500.00",
            vegaPnl = "200.00",
            thetaPnl = "-100.00",
            rhoPnl = "50.00",
            unexplainedPnl = "3695.67",
            highWaterMark = "15000.00",
            correlationId = "corr-abc-123",
        )

        val serialized = Json.encodeToString(IntradayPnlEvent.serializer(), event)
        val deserialized = json.decodeFromString<IntradayPnlEvent>(serialized)

        deserialized.bookId shouldBe "book-1"
        deserialized.snapshotAt shouldBe "2026-03-24T10:15:00Z"
        deserialized.baseCurrency shouldBe "USD"
        deserialized.trigger shouldBe "position_change"
        deserialized.totalPnl shouldBe "12345.67"
        deserialized.realisedPnl shouldBe "5000.00"
        deserialized.unrealisedPnl shouldBe "7345.67"
        deserialized.deltaPnl shouldBe "8000.00"
        deserialized.gammaPnl shouldBe "500.00"
        deserialized.vegaPnl shouldBe "200.00"
        deserialized.thetaPnl shouldBe "-100.00"
        deserialized.rhoPnl shouldBe "50.00"
        deserialized.unexplainedPnl shouldBe "3695.67"
        deserialized.highWaterMark shouldBe "15000.00"
        deserialized.correlationId shouldBe "corr-abc-123"
    }

    test("IntradayPnlEvent with null correlationId deserializes correctly") {
        val event = IntradayPnlEvent(
            bookId = "book-2",
            snapshotAt = "2026-03-24T10:16:00Z",
            baseCurrency = "EUR",
            trigger = "trade_booked",
            totalPnl = "0.00",
            realisedPnl = "0.00",
            unrealisedPnl = "0.00",
            deltaPnl = "0.00",
            gammaPnl = "0.00",
            vegaPnl = "0.00",
            thetaPnl = "0.00",
            rhoPnl = "0.00",
            unexplainedPnl = "0.00",
            highWaterMark = "0.00",
        )

        val serialized = Json.encodeToString(IntradayPnlEvent.serializer(), event)
        val deserialized = json.decodeFromString<IntradayPnlEvent>(serialized)

        deserialized.correlationId shouldBe null
        deserialized.totalPnl shouldBe "0.00"
    }

    test("consumer with ignoreUnknownKeys tolerates future fields") {
        val jsonWithExtraField = """
            {
                "bookId": "book-3",
                "snapshotAt": "2026-03-24T10:17:00Z",
                "baseCurrency": "USD",
                "trigger": "position_change",
                "totalPnl": "100.00",
                "realisedPnl": "50.00",
                "unrealisedPnl": "50.00",
                "deltaPnl": "80.00",
                "gammaPnl": "10.00",
                "vegaPnl": "5.00",
                "thetaPnl": "-2.00",
                "rhoPnl": "1.00",
                "unexplainedPnl": "6.00",
                "highWaterMark": "100.00",
                "futureField": "ignored"
            }
        """.trimIndent()

        val deserialized = json.decodeFromString<IntradayPnlEvent>(jsonWithExtraField)
        deserialized.bookId shouldBe "book-3"
        deserialized.totalPnl shouldBe "100.00"
    }

    test("bookId is the Kafka partition key") {
        val event = IntradayPnlEvent(
            bookId = "desk-fx-1",
            snapshotAt = "2026-03-24T10:18:00Z",
            baseCurrency = "USD",
            trigger = "position_change",
            totalPnl = "500.00",
            realisedPnl = "200.00",
            unrealisedPnl = "300.00",
            deltaPnl = "400.00",
            gammaPnl = "50.00",
            vegaPnl = "20.00",
            thetaPnl = "-10.00",
            rhoPnl = "5.00",
            unexplainedPnl = "35.00",
            highWaterMark = "600.00",
        )
        event.bookId shouldBe "desk-fx-1"
    }

    test("IntradayPnlEvent with instrumentPnl serializes and deserializes per-instrument breakdown") {
        val item = InstrumentPnlItem(
            instrumentId = "AAPL",
            assetClass = "EQUITY",
            totalPnl = "2000.00",
            deltaPnl = "1800.00",
            gammaPnl = "100.00",
            vegaPnl = "0.00",
            thetaPnl = "-50.00",
            rhoPnl = "10.00",
            unexplainedPnl = "140.00",
        )
        val event = IntradayPnlEvent(
            bookId = "book-1",
            snapshotAt = "2026-03-24T10:19:00Z",
            baseCurrency = "USD",
            trigger = "position_change",
            totalPnl = "2000.00",
            realisedPnl = "0.00",
            unrealisedPnl = "2000.00",
            deltaPnl = "1800.00",
            gammaPnl = "100.00",
            vegaPnl = "0.00",
            thetaPnl = "-50.00",
            rhoPnl = "10.00",
            unexplainedPnl = "140.00",
            highWaterMark = "2000.00",
            instrumentPnl = listOf(item),
        )

        val serialized = Json.encodeToString(IntradayPnlEvent.serializer(), event)
        val deserialized = json.decodeFromString<IntradayPnlEvent>(serialized)

        val items = deserialized.instrumentPnl!!
        items shouldHaveSize 1
        items[0].instrumentId shouldBe "AAPL"
        items[0].totalPnl shouldBe "2000.00"
        items[0].assetClass shouldBe "EQUITY"
    }

    test("consumer with ignoreUnknownKeys deserializes event without instrumentPnl as null") {
        val jsonWithoutInstrumentPnl = """
            {
                "bookId": "book-5",
                "snapshotAt": "2026-03-24T10:20:00Z",
                "baseCurrency": "USD",
                "trigger": "position_change",
                "totalPnl": "100.00",
                "realisedPnl": "50.00",
                "unrealisedPnl": "50.00",
                "deltaPnl": "80.00",
                "gammaPnl": "10.00",
                "vegaPnl": "5.00",
                "thetaPnl": "-2.00",
                "rhoPnl": "1.00",
                "unexplainedPnl": "6.00",
                "highWaterMark": "100.00"
            }
        """.trimIndent()

        val deserialized = json.decodeFromString<IntradayPnlEvent>(jsonWithoutInstrumentPnl)
        deserialized.instrumentPnl shouldBe null
    }
})
