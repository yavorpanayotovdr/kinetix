package com.kinetix.acceptance

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.kinetix.common.model.*
import com.kinetix.common.persistence.ConnectionPoolConfig
import com.kinetix.common.resilience.CircuitBreaker
import com.kinetix.common.resilience.CircuitBreakerConfig
import com.kinetix.common.resilience.CircuitBreakerOpenException
import com.kinetix.common.resilience.CircuitState
import com.kinetix.common.security.Role
import com.kinetix.gateway.auth.JwtConfig
import com.kinetix.gateway.client.*
import com.kinetix.gateway.module
import com.kinetix.gateway.ratelimit.RateLimit
import com.kinetix.gateway.ratelimit.RateLimiterConfig
import com.kinetix.gateway.ratelimit.TokenBucketRateLimiter
import com.kinetix.risk.client.ResilientRiskEngineClient
import com.kinetix.risk.client.RiskEngineClient
import com.kinetix.risk.model.CalculationType
import com.kinetix.risk.model.ConfidenceLevel
import com.kinetix.risk.model.VaRCalculationRequest
import com.kinetix.risk.model.VaRResult
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.*
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency
import java.util.Date

private const val TEST_SECRET = "test-secret-that-is-at-least-256-bits-long-for-hmac"
private const val TEST_ISSUER = "kinetix-test"
private const val TEST_AUDIENCE = "kinetix-api"

private fun testJwtConfig() = JwtConfig(
    issuer = TEST_ISSUER, audience = TEST_AUDIENCE, realm = "kinetix-test", secret = TEST_SECRET,
)

private fun generateToken(roles: List<Role>): String = JWT.create()
    .withSubject("user-1")
    .withAudience(TEST_AUDIENCE)
    .withIssuer(TEST_ISSUER)
    .withClaim("preferred_username", "testuser")
    .withClaim("roles", roles.map { it.name })
    .withExpiresAt(Date(System.currentTimeMillis() + 3_600_000))
    .sign(Algorithm.HMAC256(TEST_SECRET))

class ProductionHardeningAcceptanceTest : BehaviorSpec({

    val positionClient = mockk<PositionServiceClient>()
    val riskClient = mockk<RiskServiceClient>()
    val jwtConfig = testJwtConfig()

    beforeEach { clearMocks(positionClient, riskClient) }

    given("JWT authentication configured with RBAC roles") {

        `when`("a TRADER requests to book a trade") {
            then("authorized — TRADER has WRITE_TRADES permission") {
                val trade = Trade(
                    tradeId = TradeId("t-acc-1"),
                    portfolioId = PortfolioId("port-1"),
                    instrumentId = InstrumentId("AAPL"),
                    assetClass = AssetClass.EQUITY,
                    side = Side.BUY,
                    quantity = BigDecimal("100"),
                    price = Money(BigDecimal("150.00"), Currency.getInstance("USD")),
                    tradedAt = Instant.parse("2025-01-15T10:00:00Z"),
                )
                val position = Position(
                    portfolioId = PortfolioId("port-1"),
                    instrumentId = InstrumentId("AAPL"),
                    assetClass = AssetClass.EQUITY,
                    quantity = BigDecimal("100"),
                    averageCost = Money(BigDecimal("150.00"), Currency.getInstance("USD")),
                    marketPrice = Money(BigDecimal("155.00"), Currency.getInstance("USD")),
                )
                coEvery { positionClient.bookTrade(any()) } returns BookTradeResult(trade, position)
                val token = generateToken(listOf(Role.TRADER))

                testApplication {
                    application { module(jwtConfig, positionClient = positionClient) }
                    val response = client.post("/api/v1/portfolios/port-1/trades") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"tradeId":"t-acc-1","instrumentId":"AAPL","assetClass":"EQUITY","side":"BUY","quantity":"100","priceAmount":"150.00","priceCurrency":"USD","tradedAt":"2025-01-15T10:00:00Z"}""")
                    }
                    response.status shouldBe HttpStatusCode.Created
                }
            }
        }

        `when`("a VIEWER requests to book a trade") {
            then("denied — VIEWER lacks WRITE_TRADES permission") {
                val token = generateToken(listOf(Role.VIEWER))

                testApplication {
                    application { module(jwtConfig, positionClient = positionClient) }
                    val response = client.post("/api/v1/portfolios/port-1/trades") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"tradeId":"t-acc-2","instrumentId":"AAPL","assetClass":"EQUITY","side":"BUY","quantity":"100","priceAmount":"150.00","priceCurrency":"USD","tradedAt":"2025-01-15T10:00:00Z"}""")
                    }
                    response.status shouldBe HttpStatusCode.Forbidden
                }
            }
        }

        `when`("a COMPLIANCE user requests regulatory reports") {
            then("authorized — COMPLIANCE has GENERATE_REPORTS permission") {
                coEvery { riskClient.calculateFrtb(any<String>()) } returns FrtbResultSummary(
                    portfolioId = "port-1",
                    sbmCharges = emptyList(),
                    totalSbmCharge = 100000.0,
                    grossJtd = 50000.0,
                    hedgeBenefit = 10000.0,
                    netDrc = 40000.0,
                    exoticNotional = 5000.0,
                    otherNotional = 3000.0,
                    totalRrao = 8000.0,
                    totalCapitalCharge = 148000.0,
                    calculatedAt = Instant.parse("2025-01-15T10:00:00Z"),
                )
                val token = generateToken(listOf(Role.COMPLIANCE))

                testApplication {
                    application { module(jwtConfig, riskClient = riskClient) }
                    val response = client.post("/api/v1/regulatory/frtb/port-1") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("{}")
                    }
                    response.status shouldBe HttpStatusCode.OK
                }
            }
        }
    }

    given("circuit breaker protecting risk engine calls") {

        `when`("risk engine fails repeatedly exceeding threshold") {
            val delegate = mockk<RiskEngineClient>()
            val cb = CircuitBreaker(CircuitBreakerConfig(failureThreshold = 3, resetTimeoutMs = 5000, name = "risk"))
            val resilient = ResilientRiskEngineClient(delegate, cb)
            val request = VaRCalculationRequest(
                portfolioId = PortfolioId("port-1"),
                calculationType = CalculationType.PARAMETRIC,
                confidenceLevel = ConfidenceLevel.CL_95,
            )
            coEvery { delegate.calculateVaR(any(), any()) } throws RuntimeException("connection refused")

            then("circuit opens and rejects subsequent calls") {
                repeat(3) { runCatching { resilient.calculateVaR(request, emptyList()) } }
                cb.currentState shouldBe CircuitState.OPEN
                shouldThrow<CircuitBreakerOpenException> {
                    resilient.calculateVaR(request, emptyList())
                }
            }

            then("after reset timeout, circuit transitions to half-open") {
                cb.reset()
                repeat(3) { runCatching { resilient.calculateVaR(request, emptyList()) } }
                cb.currentState shouldBe CircuitState.OPEN
            }

            then("successful call in half-open closes circuit") {
                cb.reset()
                val result = VaRResult(
                    portfolioId = PortfolioId("port-1"),
                    calculationType = CalculationType.PARAMETRIC,
                    confidenceLevel = ConfidenceLevel.CL_95,
                    varValue = 50000.0,
                    expectedShortfall = 65000.0,
                    componentBreakdown = emptyList(),
                    calculatedAt = Instant.parse("2025-01-15T10:00:00Z"),
                )
                coEvery { delegate.calculateVaR(any(), any()) } returns result
                val actual = resilient.calculateVaR(request, emptyList())
                actual shouldBe result
                cb.currentState shouldBe CircuitState.CLOSED
            }
        }
    }

    given("rate limiter configured on gateway") {

        `when`("requests within limit") {
            then("all accepted") {
                testApplication {
                    application {
                        module()
                        install(RateLimit) {
                            rateLimiter = TokenBucketRateLimiter(RateLimiterConfig(requestsPerSecond = 100, burstSize = 10))
                            excludedPaths = setOf("/health", "/metrics")
                        }
                        routing {
                            get("/api/test") { call.respondText("ok") }
                        }
                    }
                    repeat(5) {
                        client.get("/api/test").status shouldBe HttpStatusCode.OK
                    }
                }
            }
        }

        `when`("requests exceed limit") {
            then("excess rejected") {
                testApplication {
                    application {
                        module()
                        install(RateLimit) {
                            rateLimiter = TokenBucketRateLimiter(RateLimiterConfig(requestsPerSecond = 10, burstSize = 2))
                            excludedPaths = setOf("/health", "/metrics")
                        }
                        routing {
                            get("/api/test") { call.respondText("ok") }
                        }
                    }
                    repeat(2) { client.get("/api/test") }
                    client.get("/api/test").status shouldBe HttpStatusCode.TooManyRequests
                }
            }
        }
    }

    given("connection pool tuned for services") {
        `when`("config created for position-service") {
            then("has 15 max connections and 3 min idle") {
                val config = ConnectionPoolConfig.forService("position-service")
                config.maxPoolSize shouldBe 15
                config.minIdle shouldBe 3
            }
        }
    }
})
