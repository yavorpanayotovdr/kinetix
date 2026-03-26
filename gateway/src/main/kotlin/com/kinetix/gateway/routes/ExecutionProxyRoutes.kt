package com.kinetix.gateway.routes

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.utils.io.*

/**
 * Gateway proxy routes for Direction 5 execution endpoints.
 * Forwards pre-trade check, execution cost, and reconciliation calls
 * to the position-service backend.
 *
 * All traffic is proxied as-is — no transformation needed at the gateway layer
 * for these endpoints. The position-service owns the business logic.
 */
fun Route.executionProxyRoutes(httpClient: HttpClient, positionUrl: String) {
    // Order submission — requires WRITE_TRADES (enforced at gateway level)
    post("/api/v1/orders") {
        proxyTo(httpClient, "$positionUrl/api/v1/orders", call)
    }

    // Pre-trade risk check (no DB persistence, <100ms target)
    post("/api/v1/risk/pre-trade-check") {
        proxyTo(httpClient, "$positionUrl/api/v1/risk/pre-trade-check", call)
    }

    // Execution cost analysis per book
    get("/api/v1/execution/cost/{bookId}") {
        val bookId = call.requirePathParam("bookId")
        proxyTo(httpClient, "$positionUrl/api/v1/execution/cost/$bookId", call)
    }

    // Prime broker reconciliation history
    get("/api/v1/execution/reconciliation/{bookId}") {
        val bookId = call.requirePathParam("bookId")
        proxyTo(httpClient, "$positionUrl/api/v1/execution/reconciliation/$bookId", call)
    }

    // Prime broker statement upload
    post("/api/v1/execution/reconciliation/{bookId}/statements") {
        val bookId = call.requirePathParam("bookId")
        proxyTo(httpClient, "$positionUrl/api/v1/execution/reconciliation/$bookId/statements", call)
    }
}

private suspend fun proxyTo(httpClient: HttpClient, upstreamUrl: String, call: io.ktor.server.application.ApplicationCall) {
    val method = call.request.httpMethod
    val requestBody: ByteArray? = if (method == HttpMethod.Post || method == HttpMethod.Put) {
        call.receiveChannel().toByteArray()
    } else null

    val response = httpClient.request(upstreamUrl) {
        this.method = method
        if (requestBody != null) {
            contentType(call.request.contentType())
            setBody(requestBody)
        }
        call.request.headers.forEach { name, values ->
            if (name !in setOf(HttpHeaders.Host, HttpHeaders.ContentLength)) {
                values.forEach { value -> header(name, value) }
            }
        }
    }

    call.respondBytes(
        bytes = response.readRawBytes(),
        contentType = response.contentType(),
        status = response.status,
    )
}
