package com.kinetix.gateway.ratelimit

import com.kinetix.gateway.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*

class RateLimitRouteTest : FunSpec({

    test("requests within rate limit return 200") {
        testApplication {
            application {
                module()
                install(RateLimit) {
                    rateLimiter = TokenBucketRateLimiter(RateLimiterConfig(requestsPerSecond = 10, burstSize = 5))
                    excludedPaths = setOf("/health", "/metrics")
                }
                routing {
                    get("/api/test") {
                        call.respondText("ok")
                    }
                }
            }
            repeat(3) {
                val response = client.get("/api/test")
                response.status shouldBe HttpStatusCode.OK
            }
        }
    }

    test("requests exceeding rate limit return 429") {
        testApplication {
            application {
                module()
                install(RateLimit) {
                    rateLimiter = TokenBucketRateLimiter(RateLimiterConfig(requestsPerSecond = 10, burstSize = 2))
                    excludedPaths = setOf("/health", "/metrics")
                }
                routing {
                    get("/api/test") {
                        call.respondText("ok")
                    }
                }
            }
            repeat(2) { client.get("/api/test") }
            val response = client.get("/api/test")
            response.status shouldBe HttpStatusCode.TooManyRequests
        }
    }

    test("rate limit response includes Retry-After header") {
        testApplication {
            application {
                module()
                install(RateLimit) {
                    rateLimiter = TokenBucketRateLimiter(RateLimiterConfig(requestsPerSecond = 10, burstSize = 1))
                    excludedPaths = setOf("/health", "/metrics")
                }
                routing {
                    get("/api/test") {
                        call.respondText("ok")
                    }
                }
            }
            client.get("/api/test")
            val response = client.get("/api/test")
            response.status shouldBe HttpStatusCode.TooManyRequests
            response.headers["Retry-After"].shouldNotBeEmpty()
        }
    }

    test("health endpoint is not rate limited") {
        testApplication {
            application {
                module()
                install(RateLimit) {
                    rateLimiter = TokenBucketRateLimiter(RateLimiterConfig(requestsPerSecond = 10, burstSize = 1))
                    excludedPaths = setOf("/health", "/metrics")
                }
            }
            repeat(5) {
                val response = client.get("/health")
                response.status shouldBe HttpStatusCode.OK
            }
        }
    }
})
