package com.kinetix.gateway

import com.kinetix.common.model.PortfolioId
import com.kinetix.common.security.Permission
import com.kinetix.gateway.auth.JwtConfig
import com.kinetix.gateway.auth.configureJwtAuth
import com.kinetix.gateway.auth.requirePermission
import com.kinetix.gateway.client.HttpNotificationServiceClient
import com.kinetix.gateway.client.HttpPositionServiceClient
import com.kinetix.gateway.client.HttpPriceServiceClient
import com.kinetix.gateway.client.HttpRiskServiceClient
import com.kinetix.gateway.client.NotificationServiceClient
import com.kinetix.gateway.client.PositionServiceClient
import com.kinetix.gateway.client.PriceServiceClient
import com.kinetix.gateway.client.RiskServiceClient
import com.kinetix.gateway.dto.*
import com.kinetix.gateway.routes.dataQualityRoutes
import com.kinetix.gateway.routes.dependenciesRoutes
import com.kinetix.gateway.routes.jobHistoryRoutes
import com.kinetix.gateway.routes.priceRoutes
import com.kinetix.gateway.routes.notificationRoutes
import com.kinetix.gateway.routes.positionRoutes
import com.kinetix.gateway.routes.regulatoryRoutes
import com.kinetix.gateway.routes.sodSnapshotRoutes
import com.kinetix.gateway.routes.stressTestRoutes
import com.kinetix.gateway.routes.requirePathParam
import com.kinetix.gateway.routes.varRoutes
import com.kinetix.gateway.websocket.PriceBroadcaster
import com.kinetix.gateway.websocket.priceWebSocket
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

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
    install(OpenApi) {
        info {
            title = "Gateway API"
            version = "1.0.0"
            description = "API gateway aggregating all Kinetix services"
        }
    }
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
        route("openapi.json") { openApi() }
        route("swagger") { swaggerUI("/openapi.json") }
    }
}

fun Application.module(positionClient: PositionServiceClient) {
    module()
    routing {
        positionRoutes(positionClient)
    }
}

fun Application.module(priceClient: PriceServiceClient) {
    module()
    routing {
        priceRoutes(priceClient)
    }
}

fun Application.module(broadcaster: PriceBroadcaster) {
    module()
    routing {
        priceWebSocket(broadcaster)
    }
}

fun Application.module(riskClient: RiskServiceClient) {
    module()
    routing {
        varRoutes(riskClient)
        stressTestRoutes(riskClient)
        regulatoryRoutes(riskClient)
        dependenciesRoutes(riskClient)
        jobHistoryRoutes(riskClient)
        sodSnapshotRoutes(riskClient)
    }
}

fun Application.module(
    positionClient: PositionServiceClient,
    priceClient: PriceServiceClient,
    broadcaster: PriceBroadcaster,
) {
    module()
    routing {
        positionRoutes(positionClient)
        priceRoutes(priceClient)
        priceWebSocket(broadcaster)
    }
}

fun Application.module(
    positionClient: PositionServiceClient,
    priceClient: PriceServiceClient,
    broadcaster: PriceBroadcaster,
    riskClient: RiskServiceClient,
) {
    module()
    routing {
        positionRoutes(positionClient)
        priceRoutes(priceClient)
        priceWebSocket(broadcaster)
        varRoutes(riskClient)
        stressTestRoutes(riskClient)
        regulatoryRoutes(riskClient)
        dependenciesRoutes(riskClient)
        jobHistoryRoutes(riskClient)
        sodSnapshotRoutes(riskClient)
    }
}

fun Application.module(notificationClient: NotificationServiceClient) {
    module()
    routing {
        notificationRoutes(notificationClient)
    }
}

fun Application.moduleWithDataQuality(
    positionClient: PositionServiceClient,
    priceClient: PriceServiceClient,
    riskClient: RiskServiceClient,
) {
    module()
    routing {
        positionRoutes(positionClient)
        priceRoutes(priceClient)
        varRoutes(riskClient)
        dataQualityRoutes()
    }
}

fun Application.devModule() {
    val servicesConfig = environment.config.config("services")
    val positionUrl = servicesConfig.property("position.url").getString()
    val priceUrl = servicesConfig.property("price.url").getString()
    val riskUrl = servicesConfig.property("risk.url").getString()
    val notificationUrl = servicesConfig.property("notification.url").getString()
    val ratesUrl = servicesConfig.property("rates.url").getString()
    val referenceDataUrl = servicesConfig.property("referenceData.url").getString()
    val volatilityUrl = servicesConfig.property("volatility.url").getString()
    val correlationUrl = servicesConfig.property("correlation.url").getString()

    val jsonConfig = Json { ignoreUnknownKeys = true }
    val httpClient = HttpClient(CIO) {
        install(ClientContentNegotiation) {
            json(jsonConfig)
        }
    }

    val positionClient = HttpPositionServiceClient(httpClient, positionUrl)
    val priceClient = HttpPriceServiceClient(httpClient, priceUrl)
    val riskClient = HttpRiskServiceClient(httpClient, riskUrl)
    val notificationClient = HttpNotificationServiceClient(httpClient, notificationUrl)
    val broadcaster = PriceBroadcaster()

    module(positionClient, priceClient, broadcaster, riskClient)
    routing {
        notificationRoutes(notificationClient)
        dataQualityRoutes()
        get("/api/v1/system/health") {
            val serviceUrls = mapOf(
                "position-service" to positionUrl,
                "price-service" to priceUrl,
                "risk-orchestrator" to riskUrl,
                "notification-service" to notificationUrl,
                "rates-service" to ratesUrl,
                "reference-data-service" to referenceDataUrl,
                "volatility-service" to volatilityUrl,
                "correlation-service" to correlationUrl,
            )
            val results = coroutineScope {
                serviceUrls.map { (name, url) ->
                    name to async {
                        try {
                            val resp = withTimeoutOrNull(2000L) {
                                httpClient.get("$url/health")
                            }
                            if (resp != null && resp.status == HttpStatusCode.OK) "UP" else "DOWN"
                        } catch (_: Exception) {
                            "DOWN"
                        }
                    }
                }.map { (name, deferred) -> name to deferred.await() }
            }
            val overall = if (results.all { it.second == "UP" }) "UP" else "DEGRADED"
            val response = buildJsonObject {
                put("status", overall)
                putJsonObject("services") {
                    putJsonObject("gateway") { put("status", "UP") }
                    for ((name, status) in results) {
                        putJsonObject(name) { put("status", status) }
                    }
                }
            }
            call.respond(response)
        }
    }
}

fun Application.module(
    jwtConfig: JwtConfig,
    positionClient: PositionServiceClient? = null,
    riskClient: RiskServiceClient? = null,
    notificationClient: NotificationServiceClient? = null,
) {
    module()
    configureJwtAuth(jwtConfig)
    routing {
        authenticate("auth-jwt") {
            if (positionClient != null) {
                requirePermission(Permission.READ_PORTFOLIOS) {
                    get("/api/v1/portfolios") {
                        val portfolios = positionClient.listPortfolios()
                        call.respond(portfolios.map { it.toResponse() })
                    }
                }
                route("/api/v1/portfolios/{portfolioId}") {
                    requirePermission(Permission.WRITE_TRADES) {
                        post("/trades") {
                            val portfolioId = PortfolioId(call.requirePathParam("portfolioId"))
                            val request = call.receive<BookTradeRequest>()
                            val command = request.toCommand(portfolioId)
                            val result = positionClient.bookTrade(command)
                            call.respond(HttpStatusCode.Created, result.toResponse())
                        }
                    }
                    requirePermission(Permission.READ_POSITIONS) {
                        get("/positions") {
                            val portfolioId = PortfolioId(call.requirePathParam("portfolioId"))
                            val positions = positionClient.getPositions(portfolioId)
                            call.respond(positions.map { it.toResponse() })
                        }
                    }
                }
            }
            if (riskClient != null) {
                requirePermission(Permission.CALCULATE_RISK) {
                    varRoutes(riskClient)
                    dependenciesRoutes(riskClient)
                    sodSnapshotRoutes(riskClient)
                }
                requirePermission(Permission.READ_RISK) {
                    stressTestRoutes(riskClient)
                    jobHistoryRoutes(riskClient)
                }
                requirePermission(Permission.READ_REGULATORY) {
                    regulatoryRoutes(riskClient)
                }
            }
            if (notificationClient != null) {
                requirePermission(Permission.READ_ALERTS) {
                    notificationRoutes(notificationClient)
                }
            }
        }
    }
}
