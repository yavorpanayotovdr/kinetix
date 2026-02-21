package com.kinetix.gateway

import com.kinetix.gateway.client.MarketDataServiceClient
import com.kinetix.gateway.client.PositionServiceClient
import com.kinetix.gateway.client.RiskServiceClient
import com.kinetix.gateway.dto.ErrorResponse
import com.kinetix.gateway.routes.marketDataRoutes
import com.kinetix.gateway.routes.positionRoutes
import com.kinetix.gateway.routes.varRoutes
import com.kinetix.gateway.websocket.PriceBroadcaster
import com.kinetix.gateway.websocket.marketDataWebSocket
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.serialization.json.Json

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(MicrometerMetrics) { registry = appMicrometerRegistry }
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
        })
    }
    install(WebSockets)
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("bad_request", cause.message ?: "Invalid request"),
            )
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("internal_error", "An unexpected error occurred"),
            )
        }
    }
    routing {
        get("/health") {
            call.respondText("""{"status":"UP"}""", ContentType.Application.Json)
        }
        get("/metrics") {
            call.respondText(appMicrometerRegistry.scrape())
        }
    }
}

fun Application.module(positionClient: PositionServiceClient) {
    module()
    routing {
        positionRoutes(positionClient)
    }
}

fun Application.module(marketDataClient: MarketDataServiceClient) {
    module()
    routing {
        marketDataRoutes(marketDataClient)
    }
}

fun Application.module(broadcaster: PriceBroadcaster) {
    module()
    routing {
        marketDataWebSocket(broadcaster)
    }
}

fun Application.module(riskClient: RiskServiceClient) {
    module()
    routing {
        varRoutes(riskClient)
    }
}

fun Application.module(
    positionClient: PositionServiceClient,
    marketDataClient: MarketDataServiceClient,
    broadcaster: PriceBroadcaster,
) {
    module()
    routing {
        positionRoutes(positionClient)
        marketDataRoutes(marketDataClient)
        marketDataWebSocket(broadcaster)
    }
}

fun Application.module(
    positionClient: PositionServiceClient,
    marketDataClient: MarketDataServiceClient,
    broadcaster: PriceBroadcaster,
    riskClient: RiskServiceClient,
) {
    module()
    routing {
        positionRoutes(positionClient)
        marketDataRoutes(marketDataClient)
        marketDataWebSocket(broadcaster)
        varRoutes(riskClient)
    }
}
