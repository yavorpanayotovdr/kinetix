package com.kinetix.risk.routes

import com.kinetix.common.resilience.CircuitBreakerOpenException
import com.kinetix.risk.ErrorBody
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json

class RiskRoutesErrorHandlingTest : FunSpec({

    fun ApplicationTestBuilder.configureErrorHandling(throwable: Throwable) {
        install(ContentNegotiation) { json() }
        install(StatusPages) {
            exception<IllegalArgumentException> { call, cause ->
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorBody("bad_request", cause.message ?: "Invalid request"),
                )
            }
            exception<CircuitBreakerOpenException> { call, _ ->
                call.response.header("Retry-After", "30")
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    ErrorBody("service_unavailable", "Risk engine temporarily unavailable"),
                )
            }
            exception<StatusRuntimeException> { call, cause ->
                when (cause.status.code) {
                    Status.Code.DEADLINE_EXCEEDED -> {
                        call.respond(
                            HttpStatusCode.GatewayTimeout,
                            ErrorBody("gateway_timeout", "Risk calculation timed out"),
                        )
                    }
                    Status.Code.INVALID_ARGUMENT -> {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorBody("bad_request", cause.status.description ?: "Invalid argument"),
                        )
                    }
                    Status.Code.UNAVAILABLE, Status.Code.RESOURCE_EXHAUSTED -> {
                        call.response.header("Retry-After", "5")
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            ErrorBody("service_unavailable", cause.status.description ?: "Service unavailable"),
                        )
                    }
                    else -> {
                        call.respond(
                            HttpStatusCode.BadGateway,
                            ErrorBody("bad_gateway", cause.status.description ?: "Risk engine error"),
                        )
                    }
                }
            }
            exception<Throwable> { call, _ ->
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorBody("internal_error", "An unexpected error occurred"),
                )
            }
        }
        routing {
            get("/test") {
                throw throwable
            }
        }
    }

    test("returns 503 with Retry-After when circuit breaker is open") {
        testApplication {
            configureErrorHandling(CircuitBreakerOpenException("risk-engine"))
            val response = client.get("/test")
            response.status shouldBe HttpStatusCode.ServiceUnavailable
            response.headers["Retry-After"] shouldBe "30"
            val body = Json.decodeFromString<ErrorBody>(response.bodyAsText())
            body.error shouldBe "service_unavailable"
        }
    }

    test("returns 504 on gRPC DEADLINE_EXCEEDED") {
        testApplication {
            configureErrorHandling(StatusRuntimeException(Status.DEADLINE_EXCEEDED))
            val response = client.get("/test")
            response.status shouldBe HttpStatusCode.GatewayTimeout
            val body = Json.decodeFromString<ErrorBody>(response.bodyAsText())
            body.error shouldBe "gateway_timeout"
        }
    }

    test("returns 400 on gRPC INVALID_ARGUMENT") {
        testApplication {
            configureErrorHandling(
                StatusRuntimeException(Status.INVALID_ARGUMENT.withDescription("empty positions")),
            )
            val response = client.get("/test")
            response.status shouldBe HttpStatusCode.BadRequest
            val body = Json.decodeFromString<ErrorBody>(response.bodyAsText())
            body.error shouldBe "bad_request"
            body.message shouldContain "empty positions"
        }
    }

    test("returns 503 with Retry-After on gRPC UNAVAILABLE") {
        testApplication {
            configureErrorHandling(
                StatusRuntimeException(Status.UNAVAILABLE.withDescription("connection refused")),
            )
            val response = client.get("/test")
            response.status shouldBe HttpStatusCode.ServiceUnavailable
            response.headers["Retry-After"] shouldBe "5"
            val body = Json.decodeFromString<ErrorBody>(response.bodyAsText())
            body.error shouldBe "service_unavailable"
            body.message shouldContain "connection refused"
        }
    }

    test("returns 503 with Retry-After on gRPC RESOURCE_EXHAUSTED") {
        testApplication {
            configureErrorHandling(
                StatusRuntimeException(Status.RESOURCE_EXHAUSTED.withDescription("too many requests")),
            )
            val response = client.get("/test")
            response.status shouldBe HttpStatusCode.ServiceUnavailable
            response.headers["Retry-After"] shouldBe "5"
        }
    }

    test("returns 502 with error message on gRPC INTERNAL") {
        testApplication {
            configureErrorHandling(
                StatusRuntimeException(Status.INTERNAL.withDescription("numpy error")),
            )
            val response = client.get("/test")
            response.status shouldBe HttpStatusCode.BadGateway
            val body = Json.decodeFromString<ErrorBody>(response.bodyAsText())
            body.error shouldBe "bad_gateway"
            body.message shouldContain "numpy error"
        }
    }
})
