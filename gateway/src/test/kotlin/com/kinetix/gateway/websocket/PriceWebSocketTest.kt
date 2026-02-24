package com.kinetix.gateway.websocket

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.PricePoint
import com.kinetix.common.model.PriceSource
import com.kinetix.common.model.Money
import com.kinetix.gateway.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.plugins.websocket.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

private val USD = Currency.getInstance("USD")

private fun aaplPrice() = PricePoint(
    instrumentId = InstrumentId("AAPL"),
    price = Money(BigDecimal("170.50"), USD),
    timestamp = Instant.parse("2025-06-15T14:30:00Z"),
    source = PriceSource.BLOOMBERG,
)

private fun msftPrice() = PricePoint(
    instrumentId = InstrumentId("MSFT"),
    price = Money(BigDecimal("400.00"), USD),
    timestamp = Instant.parse("2025-06-15T14:30:00Z"),
    source = PriceSource.REUTERS,
)

class PriceWebSocketTest : FunSpec({

    test("subscribe then receive price update") {
        val broadcaster = PriceBroadcaster()

        testApplication {
            application { module(broadcaster) }
            val client = createClient { install(ClientWebSockets) }

            client.webSocket("/ws/prices") {
                send(Frame.Text("""{"type":"subscribe","instrumentIds":["AAPL"]}"""))
                delay(50)

                broadcaster.broadcast(aaplPrice())

                val frame = incoming.receive() as Frame.Text
                val json = Json.parseToJsonElement(frame.readText()).jsonObject
                json["type"]?.jsonPrimitive?.content shouldBe "price"
                json["instrumentId"]?.jsonPrimitive?.content shouldBe "AAPL"
                json["priceAmount"]?.jsonPrimitive?.content shouldBe "170.50"
                json["priceCurrency"]?.jsonPrimitive?.content shouldBe "USD"
                json["timestamp"]?.jsonPrimitive?.content shouldBe "2025-06-15T14:30:00Z"
                json["source"]?.jsonPrimitive?.content shouldBe "BLOOMBERG"
            }
        }
    }

    test("only subscribed instrument prices are received") {
        val broadcaster = PriceBroadcaster()

        testApplication {
            application { module(broadcaster) }
            val client = createClient { install(ClientWebSockets) }

            client.webSocket("/ws/prices") {
                send(Frame.Text("""{"type":"subscribe","instrumentIds":["AAPL"]}"""))
                delay(50)

                broadcaster.broadcast(msftPrice())
                broadcaster.broadcast(aaplPrice())

                val frame = incoming.receive() as Frame.Text
                val json = Json.parseToJsonElement(frame.readText()).jsonObject
                json["instrumentId"]?.jsonPrimitive?.content shouldBe "AAPL"
            }
        }
    }

    test("unsubscribe stops delivery") {
        val broadcaster = PriceBroadcaster()

        testApplication {
            application { module(broadcaster) }
            val client = createClient { install(ClientWebSockets) }

            client.webSocket("/ws/prices") {
                send(Frame.Text("""{"type":"subscribe","instrumentIds":["AAPL"]}"""))
                delay(50)
                send(Frame.Text("""{"type":"unsubscribe","instrumentIds":["AAPL"]}"""))
                delay(50)

                broadcaster.broadcast(aaplPrice())

                // Subscribe to MSFT as a sentinel to confirm we can still receive
                send(Frame.Text("""{"type":"subscribe","instrumentIds":["MSFT"]}"""))
                delay(50)
                broadcaster.broadcast(msftPrice())

                val frame = incoming.receive() as Frame.Text
                val json = Json.parseToJsonElement(frame.readText()).jsonObject
                json["instrumentId"]?.jsonPrimitive?.content shouldBe "MSFT"
            }
        }
    }

    test("unknown message type returns error") {
        val broadcaster = PriceBroadcaster()

        testApplication {
            application { module(broadcaster) }
            val client = createClient { install(ClientWebSockets) }

            client.webSocket("/ws/prices") {
                send(Frame.Text("""{"type":"invalid","instrumentIds":["AAPL"]}"""))

                val frame = incoming.receive() as Frame.Text
                frame.readText() shouldContain "Unknown message type"
            }
        }
    }

    test("malformed JSON returns error") {
        val broadcaster = PriceBroadcaster()

        testApplication {
            application { module(broadcaster) }
            val client = createClient { install(ClientWebSockets) }

            client.webSocket("/ws/prices") {
                send(Frame.Text("not json"))

                val frame = incoming.receive() as Frame.Text
                frame.readText() shouldContain "Invalid JSON"
            }
        }
    }
})
