package com.kinetix.gateway.websocket

import com.kinetix.common.security.Role
import com.kinetix.gateway.auth.TestJwtHelper
import com.kinetix.gateway.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.plugins.websocket.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException

class WebSocketAuthTest : FunSpec({

    val jwtConfig = TestJwtHelper.testJwtConfig()
    val jwkProvider = TestJwtHelper.testJwkProvider()

    test("WebSocket price connection is rejected without a token (channel closed immediately)") {
        val broadcaster = PriceBroadcaster()

        testApplication {
            application { module(jwtConfig, broadcaster, jwkProvider) }
            val client = createClient { install(ClientWebSockets) }

            var connectionClosed = false
            try {
                client.webSocket("/ws/prices") {
                    // Server should close immediately — incoming channel will be closed
                    for (frame in incoming) {
                        if (frame is Frame.Close) {
                            connectionClosed = true
                            break
                        }
                    }
                    connectionClosed = true
                }
            } catch (_: ClosedReceiveChannelException) {
                connectionClosed = true
            }
            connectionClosed shouldBe true
        }
    }

    test("WebSocket price connection is rejected with an invalid token (channel closed immediately)") {
        val broadcaster = PriceBroadcaster()
        val badToken = TestJwtHelper.generateTokenWithWrongSignature()

        testApplication {
            application { module(jwtConfig, broadcaster, jwkProvider) }
            val client = createClient { install(ClientWebSockets) }

            var connectionClosed = false
            try {
                client.webSocket("/ws/prices?token=$badToken") {
                    for (frame in incoming) {
                        if (frame is Frame.Close) {
                            connectionClosed = true
                            break
                        }
                    }
                    connectionClosed = true
                }
            } catch (_: ClosedReceiveChannelException) {
                connectionClosed = true
            }
            connectionClosed shouldBe true
        }
    }

    test("WebSocket price connection is rejected with unknown kid (channel closed immediately)") {
        val broadcaster = PriceBroadcaster()
        val badToken = TestJwtHelper.generateTokenWithUnknownKid()

        testApplication {
            application { module(jwtConfig, broadcaster, jwkProvider) }
            val client = createClient { install(ClientWebSockets) }

            var connectionClosed = false
            try {
                client.webSocket("/ws/prices?token=$badToken") {
                    for (frame in incoming) {
                        if (frame is Frame.Close) {
                            connectionClosed = true
                            break
                        }
                    }
                    connectionClosed = true
                }
            } catch (_: ClosedReceiveChannelException) {
                connectionClosed = true
            }
            connectionClosed shouldBe true
        }
    }

    test("WebSocket price connection succeeds with a valid token") {
        val broadcaster = PriceBroadcaster()
        val token = TestJwtHelper.generateToken(roles = listOf(Role.TRADER))

        testApplication {
            application { module(jwtConfig, broadcaster, jwkProvider) }
            val client = createClient { install(ClientWebSockets) }

            client.webSocket("/ws/prices?token=$token") {
                send(Frame.Text("""{"type":"subscribe","instrumentIds":["AAPL"]}"""))
                // Connection stays open; no close frame received immediately
                outgoing.close()
            }
        }
    }

    test("WebSocket PnL connection is rejected without a token (channel closed immediately)") {
        val broadcaster = PnlBroadcaster()

        testApplication {
            application { module(jwtConfig, broadcaster, jwkProvider) }
            val client = createClient { install(ClientWebSockets) }

            var connectionClosed = false
            try {
                client.webSocket("/ws/pnl") {
                    for (frame in incoming) {
                        if (frame is Frame.Close) {
                            connectionClosed = true
                            break
                        }
                    }
                    connectionClosed = true
                }
            } catch (_: ClosedReceiveChannelException) {
                connectionClosed = true
            }
            connectionClosed shouldBe true
        }
    }

    test("WebSocket PnL connection succeeds with a valid token") {
        val broadcaster = PnlBroadcaster()
        val token = TestJwtHelper.generateToken(roles = listOf(Role.TRADER))

        testApplication {
            application { module(jwtConfig, broadcaster, jwkProvider) }
            val client = createClient { install(ClientWebSockets) }

            client.webSocket("/ws/pnl?token=$token") {
                send(Frame.Text("""{"type":"subscribe","bookId":"book-1"}"""))
                outgoing.close()
            }
        }
    }

    test("WebSocket alerts connection is rejected without a token (channel closed immediately)") {
        val broadcaster = AlertBroadcaster()

        testApplication {
            application { module(jwtConfig, broadcaster, jwkProvider) }
            val client = createClient { install(ClientWebSockets) }

            var connectionClosed = false
            try {
                client.webSocket("/ws/alerts") {
                    for (frame in incoming) {
                        if (frame is Frame.Close) {
                            connectionClosed = true
                            break
                        }
                    }
                    connectionClosed = true
                }
            } catch (_: ClosedReceiveChannelException) {
                connectionClosed = true
            }
            connectionClosed shouldBe true
        }
    }

    test("WebSocket alerts connection succeeds with a valid token") {
        val broadcaster = AlertBroadcaster()
        val token = TestJwtHelper.generateToken(roles = listOf(Role.TRADER))

        testApplication {
            application { module(jwtConfig, broadcaster, jwkProvider) }
            val client = createClient { install(ClientWebSockets) }

            client.webSocket("/ws/alerts?token=$token") {
                outgoing.close()
            }
        }
    }
})
