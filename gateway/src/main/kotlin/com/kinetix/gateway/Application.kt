package com.kinetix.gateway

import com.kinetix.common.model.BookId
import com.kinetix.common.security.Permission
import com.kinetix.gateway.auth.BookAccessService
import com.kinetix.gateway.auth.InMemoryBookAccessService
import com.kinetix.gateway.auth.JwtConfig
import com.kinetix.gateway.auth.checkBookAccess
import com.kinetix.gateway.auth.configureJwtAuth
import com.kinetix.gateway.auth.requirePermission
import com.kinetix.gateway.client.HttpNotificationServiceClient
import com.kinetix.gateway.client.HttpPositionServiceClient
import com.kinetix.gateway.client.HttpPriceServiceClient
import com.kinetix.gateway.client.HttpRegulatoryServiceClient
import com.kinetix.gateway.client.HttpRiskServiceClient
import com.kinetix.gateway.client.NotificationServiceClient
import com.kinetix.gateway.client.PositionServiceClient
import com.kinetix.gateway.client.PriceServiceClient
import com.kinetix.gateway.client.RegulatoryServiceClient
import com.kinetix.gateway.client.RiskServiceClient
import com.kinetix.gateway.dto.*
import com.kinetix.gateway.routes.backtestProxyRoutes
import com.kinetix.gateway.routes.dataQualityRoutes
import com.kinetix.gateway.routes.intradayPnlProxyRoutes
import com.kinetix.gateway.routes.dependenciesRoutes
import com.kinetix.gateway.routes.eodTimelineRoutes
import com.kinetix.gateway.routes.jobHistoryRoutes
import com.kinetix.gateway.routes.priceRoutes
import com.kinetix.gateway.routes.auditProxyRoutes
import com.kinetix.gateway.routes.instrumentRoutes
import com.kinetix.gateway.routes.notificationRoutes
import com.kinetix.gateway.routes.positionRoutes
import com.kinetix.gateway.routes.regulatoryRoutes
import com.kinetix.gateway.routes.runComparisonRoutes
import com.kinetix.gateway.routes.sodSnapshotRoutes
import com.kinetix.gateway.routes.stressScenarioRoutes
import com.kinetix.gateway.routes.stressTestRoutes
import com.kinetix.gateway.routes.whatIfRoutes
import com.kinetix.gateway.routes.positionRiskRoutes
import com.kinetix.gateway.routes.requirePathParam
import com.kinetix.gateway.routes.crossBookVaRRoutes
import com.kinetix.gateway.routes.factorRiskRoutes
import com.kinetix.gateway.routes.croReportRoutes
import com.kinetix.gateway.routes.hierarchyRiskRoutes
import com.kinetix.gateway.routes.riskBudgetRoutes
import com.kinetix.gateway.routes.executionProxyRoutes
import com.kinetix.gateway.routes.liquidityRiskRoutes
import com.kinetix.gateway.routes.marketRegimeRoutes
import com.kinetix.gateway.routes.varRoutes
import com.kinetix.gateway.routes.hedgeRecommendationRoutes
import com.kinetix.gateway.routes.counterpartyRiskRoutes
import com.kinetix.gateway.kafka.KafkaIntradayPnlConsumer
import com.kinetix.gateway.websocket.AlertBroadcaster
import com.kinetix.gateway.websocket.PnlBroadcaster
import com.kinetix.gateway.websocket.PriceBroadcaster
import com.kinetix.gateway.websocket.alertWebSocket
import com.kinetix.gateway.websocket.pnlWebSocket
import com.kinetix.gateway.websocket.priceWebSocket
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import java.util.Properties
import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.plugins.statuspages.*
import org.slf4j.event.Level
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    log.info("Starting gateway")
    val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(MicrometerMetrics) { registry = appMicrometerRegistry }
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
    }
    install(CallLogging) {
        level = Level.INFO
        mdc("correlationId") {
            it.request.header("X-Correlation-ID") ?: java.util.UUID.randomUUID().toString()
        }
        mdc("userId") {
            it.authentication.principal<com.kinetix.gateway.auth.JwtUserPrincipal>()?.user?.userId ?: "anonymous"
        }
    }
    install(WebSockets) {
        pingPeriodMillis = 30_000
        timeoutMillis = 10_000
    }
    install(OpenApi) {
        info {
            title = "Gateway API"
            version = "1.0.0"
            description = "API gateway aggregating all Kinetix services"
        }
    }
    install(StatusPages) {
        exception<com.kinetix.gateway.client.ServiceUnavailableException> { call, cause ->
            cause.retryAfterSeconds?.let { call.response.header(HttpHeaders.RetryAfter, it.toString()) }
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ErrorResponse("service_unavailable", cause.message ?: "Service unavailable"),
            )
        }
        exception<com.kinetix.gateway.client.GatewayTimeoutException> { call, cause ->
            call.respond(
                HttpStatusCode.GatewayTimeout,
                ErrorResponse("gateway_timeout", cause.message ?: "Gateway timeout"),
            )
        }
        exception<com.kinetix.gateway.client.UpstreamErrorException> { call, cause ->
            call.respond(
                HttpStatusCode.fromValue(cause.statusCode),
                ErrorResponse("upstream_error", cause.message ?: "Upstream error"),
            )
        }
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

fun Application.module(broadcaster: PnlBroadcaster) {
    module()
    routing {
        pnlWebSocket(broadcaster)
    }
}

fun Application.module(riskClient: RiskServiceClient) {
    module()
    routing {
        varRoutes(riskClient)
        crossBookVaRRoutes(riskClient)
        hierarchyRiskRoutes(riskClient)
        riskBudgetRoutes(riskClient)
        croReportRoutes(riskClient)
        liquidityRiskRoutes(riskClient)
        factorRiskRoutes(riskClient)
        stressTestRoutes(riskClient)
        whatIfRoutes(riskClient)
        positionRiskRoutes(riskClient)
        regulatoryRoutes(riskClient)
        dependenciesRoutes(riskClient)
        jobHistoryRoutes(riskClient)
        eodTimelineRoutes(riskClient)
        sodSnapshotRoutes(riskClient)
        runComparisonRoutes(riskClient)
        marketRegimeRoutes(riskClient)
        hedgeRecommendationRoutes(riskClient)
        counterpartyRiskRoutes(riskClient)
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
        crossBookVaRRoutes(riskClient)
        hierarchyRiskRoutes(riskClient)
        riskBudgetRoutes(riskClient)
        croReportRoutes(riskClient)
        liquidityRiskRoutes(riskClient)
        factorRiskRoutes(riskClient)
        stressTestRoutes(riskClient)
        whatIfRoutes(riskClient)
        positionRiskRoutes(riskClient)
        regulatoryRoutes(riskClient)
        dependenciesRoutes(riskClient)
        jobHistoryRoutes(riskClient)
        eodTimelineRoutes(riskClient)
        sodSnapshotRoutes(riskClient)
        runComparisonRoutes(riskClient)
        marketRegimeRoutes(riskClient)
        hedgeRecommendationRoutes(riskClient)
        counterpartyRiskRoutes(riskClient)
    }
}

fun Application.module(notificationClient: NotificationServiceClient) {
    module()
    routing {
        notificationRoutes(notificationClient)
    }
}

fun Application.module(regulatoryClient: RegulatoryServiceClient) {
    module()
    routing {
        stressScenarioRoutes(regulatoryClient)
        backtestProxyRoutes(regulatoryClient)
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
    val regulatoryUrl = servicesConfig.property("regulatory.url").getString()
    val auditUrl = servicesConfig.property("audit.url").getString()

    val jwtCfg = environment.config.config("jwt")
    val jwtConfig = JwtConfig(
        issuer = jwtCfg.property("issuer").getString(),
        audience = jwtCfg.property("audience").getString(),
        realm = jwtCfg.property("realm").getString(),
        secret = jwtCfg.property("secret").getString(),
    )

    val jsonConfig = Json { ignoreUnknownKeys = true }
    val httpClient = HttpClient(CIO) {
        install(ClientContentNegotiation) {
            json(jsonConfig)
        }
    }

    val kafkaBootstrapServers = environment.config
        .propertyOrNull("kafka.bootstrapServers")?.getString() ?: "localhost:9092"

    val positionClient = HttpPositionServiceClient(httpClient, positionUrl)
    val priceClient = HttpPriceServiceClient(httpClient, priceUrl)
    val riskClient = HttpRiskServiceClient(httpClient, riskUrl)
    val notificationClient = HttpNotificationServiceClient(httpClient, notificationUrl)
    val regulatoryClient = HttpRegulatoryServiceClient(httpClient, regulatoryUrl)
    val priceBroadcaster = PriceBroadcaster()
    val pnlBroadcaster = PnlBroadcaster()
    val alertBroadcaster = AlertBroadcaster()

    module()
    configureJwtAuth(jwtConfig)
    routing {
        // WebSocket routes validate JWT via query parameter
        priceWebSocket(priceBroadcaster, jwtConfig)
        pnlWebSocket(pnlBroadcaster, jwtConfig)
        alertWebSocket(alertBroadcaster, jwtConfig)

        // All HTTP API routes require a valid JWT
        authenticate("auth-jwt") {
            requirePermission(Permission.READ_PORTFOLIOS) {
                positionRoutes(positionClient)
                priceRoutes(priceClient)
            }
            requirePermission(Permission.CALCULATE_RISK) {
                varRoutes(riskClient)
                crossBookVaRRoutes(riskClient)
                hierarchyRiskRoutes(riskClient)
                riskBudgetRoutes(riskClient)
                croReportRoutes(riskClient)
                liquidityRiskRoutes(riskClient)
                factorRiskRoutes(riskClient)
                whatIfRoutes(riskClient)
                positionRiskRoutes(riskClient)
                dependenciesRoutes(riskClient)
                sodSnapshotRoutes(riskClient)
                runComparisonRoutes(riskClient)
                intradayPnlProxyRoutes(riskClient)
            }
            requirePermission(Permission.READ_RISK) {
                stressTestRoutes(riskClient)
                jobHistoryRoutes(riskClient)
                marketRegimeRoutes(riskClient)
                hedgeRecommendationRoutes(riskClient)
                counterpartyRiskRoutes(riskClient)
            }
            requirePermission(Permission.READ_REGULATORY) {
                regulatoryRoutes(riskClient)
                eodTimelineRoutes(riskClient)
            }
            requirePermission(Permission.READ_ALERTS) {
                notificationRoutes(notificationClient)
            }
            requirePermission(Permission.MANAGE_SCENARIOS) {
                stressScenarioRoutes(regulatoryClient)
                backtestProxyRoutes(regulatoryClient)
            }
            requirePermission(Permission.READ_POSITIONS) {
                instrumentRoutes(httpClient, referenceDataUrl)
                executionProxyRoutes(httpClient, positionUrl)
                dataQualityRoutes(httpClient, positionUrl)
            }
            requirePermission(Permission.READ_AUDIT) {
                auditProxyRoutes(httpClient, auditUrl)
            }
            // System health is admin-only as it reveals internal service topology
            requirePermission(Permission.READ_PORTFOLIOS) {
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
                        "regulatory-service" to regulatoryUrl,
                        "audit-service" to auditUrl,
                    )
                    val results = coroutineScope {
                        serviceUrls.map { (name, url) ->
                            name to async {
                                try {
                                    val resp = withTimeoutOrNull(5000L) {
                                        httpClient.get("$url/health/ready")
                                    }
                                    if (resp != null && resp.status == HttpStatusCode.OK) "READY" else "NOT_READY"
                                } catch (_: Exception) {
                                    "DOWN"
                                }
                            }
                        }.map { (name, deferred) -> name to deferred.await() }
                    }
                    val overall = if (results.all { it.second == "READY" }) "UP" else "DEGRADED"
                    val response = buildJsonObject {
                        put("status", overall)
                        putJsonObject("services") {
                            putJsonObject("gateway") { put("status", "READY") }
                            for ((name, status) in results) {
                                putJsonObject(name) { put("status", status) }
                            }
                        }
                    }
                    call.respond(response)
                }
            }
        }
    }

    val pnlConsumerProps = Properties().apply {
        put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers)
        put(ConsumerConfig.GROUP_ID_CONFIG, "gateway-pnl-broadcaster")
        put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
        put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
        put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
        put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
    }
    val pnlKafkaConsumer = KafkaConsumer<String, String>(pnlConsumerProps)
    launch { KafkaIntradayPnlConsumer(pnlKafkaConsumer, pnlBroadcaster).start() }
}

fun Application.module(jwtConfig: JwtConfig, broadcaster: PriceBroadcaster) {
    module()
    configureJwtAuth(jwtConfig)
    routing {
        priceWebSocket(broadcaster, jwtConfig)
    }
}

fun Application.module(jwtConfig: JwtConfig, broadcaster: PnlBroadcaster) {
    module()
    configureJwtAuth(jwtConfig)
    routing {
        pnlWebSocket(broadcaster, jwtConfig)
    }
}

fun Application.module(jwtConfig: JwtConfig, broadcaster: AlertBroadcaster) {
    module()
    configureJwtAuth(jwtConfig)
    routing {
        alertWebSocket(broadcaster, jwtConfig)
    }
}

fun Application.module(
    jwtConfig: JwtConfig,
    positionClient: PositionServiceClient? = null,
    riskClient: RiskServiceClient? = null,
    notificationClient: NotificationServiceClient? = null,
    regulatoryClient: RegulatoryServiceClient? = null,
    httpClient: io.ktor.client.HttpClient? = null,
    auditBaseUrl: String? = null,
    bookAccessService: BookAccessService = InMemoryBookAccessService(),
) {
    module()
    configureJwtAuth(jwtConfig)
    routing {
        authenticate("auth-jwt") {
            if (positionClient != null) {
                requirePermission(Permission.READ_PORTFOLIOS) {
                    get("/api/v1/books") {
                        val portfolios = positionClient.listPortfolios()
                        call.respond(portfolios.map { it.toResponse() })
                    }
                }
                route("/api/v1/books/{bookId}") {
                    requirePermission(Permission.WRITE_TRADES) {
                        post("/trades") {
                            val rawBookId = call.requirePathParam("bookId")
                            if (!call.checkBookAccess(rawBookId, bookAccessService)) return@post
                            val bookId = BookId(rawBookId)
                            val request = call.receive<BookTradeRequest>()
                            val command = request.toCommand(bookId)
                            val result = positionClient.bookTrade(command)
                            call.respond(HttpStatusCode.Created, result.toResponse())
                        }
                    }
                    requirePermission(Permission.READ_POSITIONS) {
                        get("/positions") {
                            val rawBookId = call.requirePathParam("bookId")
                            if (!call.checkBookAccess(rawBookId, bookAccessService)) return@get
                            val bookId = BookId(rawBookId)
                            val positions = positionClient.getPositions(bookId)
                            call.respond(positions.map { it.toResponse() })
                        }
                    }
                }
            }
            if (riskClient != null) {
                requirePermission(Permission.CALCULATE_RISK) {
                    varRoutes(riskClient)
                    crossBookVaRRoutes(riskClient)
                    whatIfRoutes(riskClient)
                    positionRiskRoutes(riskClient)
                    dependenciesRoutes(riskClient)
                    sodSnapshotRoutes(riskClient)
                }
                requirePermission(Permission.READ_RISK) {
                    stressTestRoutes(riskClient)
                    jobHistoryRoutes(riskClient)
                    marketRegimeRoutes(riskClient)
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
            if (regulatoryClient != null) {
                requirePermission(Permission.MANAGE_SCENARIOS) {
                    stressScenarioRoutes(regulatoryClient)
                    backtestProxyRoutes(regulatoryClient)
                }
            }
            if (httpClient != null && auditBaseUrl != null) {
                requirePermission(Permission.READ_AUDIT) {
                    auditProxyRoutes(httpClient, auditBaseUrl)
                }
            }
        }
    }
}
