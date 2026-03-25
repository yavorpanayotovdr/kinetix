package com.kinetix.gateway.routes

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Proxies strategy CRUD requests to the position-service.
 *
 * Routes:
 *   POST   /api/v1/books/{bookId}/strategies
 *   GET    /api/v1/books/{bookId}/strategies
 *   GET    /api/v1/books/{bookId}/strategies/{strategyId}
 *   POST   /api/v1/books/{bookId}/strategies/{strategyId}/trades
 */
fun Route.strategyProxyRoutes(httpClient: HttpClient, positionServiceBaseUrl: String) {
    route("/api/v1/books/{bookId}/strategies") {

        post {
            val bookId = call.requirePathParam("bookId")
            val body = call.receiveText()
            val upstream = httpClient.post("$positionServiceBaseUrl/api/v1/books/$bookId/strategies") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            call.respondWith(upstream)
        }

        get {
            val bookId = call.requirePathParam("bookId")
            val upstream = httpClient.get("$positionServiceBaseUrl/api/v1/books/$bookId/strategies")
            call.respondWith(upstream)
        }

        route("/{strategyId}") {

            get {
                val bookId = call.requirePathParam("bookId")
                val strategyId = call.parameters["strategyId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val upstream = httpClient.get("$positionServiceBaseUrl/api/v1/books/$bookId/strategies/$strategyId")
                call.respondWith(upstream)
            }

            route("/trades") {
                post {
                    val bookId = call.requirePathParam("bookId")
                    val strategyId = call.parameters["strategyId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val body = call.receiveText()
                    val upstream = httpClient.post("$positionServiceBaseUrl/api/v1/books/$bookId/strategies/$strategyId/trades") {
                        contentType(ContentType.Application.Json)
                        setBody(body)
                    }
                    call.respondWith(upstream)
                }
            }
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.respondWith(upstream: HttpResponse) {
    val body = upstream.bodyAsText()
    respond(upstream.status, body)
}
