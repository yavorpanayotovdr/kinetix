package com.kinetix.risk

import com.kinetix.position.persistence.DatabaseConfig as PositionDatabaseConfig
import com.kinetix.position.persistence.DatabaseFactory as PositionDatabaseFactory
import com.kinetix.position.persistence.ExposedPositionRepository
import com.kinetix.proto.risk.MarketDataDependenciesServiceGrpcKt
import com.kinetix.proto.risk.RiskCalculationServiceGrpcKt
import com.kinetix.proto.risk.RegulatoryReportingServiceGrpcKt
import com.kinetix.proto.risk.StressTestServiceGrpcKt
import com.kinetix.risk.cache.LatestVaRCache
import com.kinetix.risk.client.GrpcRiskEngineClient
import com.kinetix.risk.client.HttpPriceServiceClient
import com.kinetix.risk.client.HttpRatesServiceClient
import com.kinetix.risk.client.PositionServicePositionProvider
import com.kinetix.risk.kafka.NoOpRiskResultPublisher
import com.kinetix.risk.routes.riskRoutes
import com.kinetix.risk.schedule.ScheduledVaRCalculator
import com.kinetix.risk.service.DependenciesDiscoverer
import com.kinetix.risk.service.MarketDataFetcher
import com.kinetix.risk.service.VaRCalculationService
import io.grpc.ManagedChannelBuilder
import io.grpc.TlsChannelCredentials
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.serialization.kotlinx.json.*
import java.io.File
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(MicrometerMetrics) { registry = appMicrometerRegistry }
    install(ContentNegotiation) { json() }
    routing {
        get("/health") {
            call.respondText("""{"status":"UP"}""", ContentType.Application.Json)
        }
        get("/metrics") {
            call.respondText(appMicrometerRegistry.scrape())
        }
    }
}

@Serializable
private data class ErrorBody(val error: String, val message: String)

fun Application.moduleWithRoutes() {
    val dbConfig = environment.config.config("database")
    val db = PositionDatabaseFactory.init(
        PositionDatabaseConfig(
            jdbcUrl = dbConfig.property("jdbcUrl").getString(),
            username = dbConfig.property("username").getString(),
            password = dbConfig.property("password").getString(),
        )
    )

    val grpcConfig = environment.config.config("grpc")
    val grpcHost = grpcConfig.property("host").getString()
    val grpcPort = grpcConfig.property("port").getString().toInt()

    val tlsEnabled = grpcConfig.propertyOrNull("tls.enabled")?.getString()?.toBoolean() ?: false
    val channel = if (tlsEnabled) {
        val caPath = grpcConfig.property("tls.caPath").getString()
        val creds = TlsChannelCredentials.newBuilder()
            .trustManager(File(caPath))
            .build()
        io.grpc.Grpc.newChannelBuilder("$grpcHost:$grpcPort", creds).build()
    } else {
        ManagedChannelBuilder
            .forAddress(grpcHost, grpcPort)
            .usePlaintext()
            .build()
    }

    val positionRepository = ExposedPositionRepository(db)
    val positionProvider = PositionServicePositionProvider(positionRepository)
    val dependenciesStub = MarketDataDependenciesServiceGrpcKt.MarketDataDependenciesServiceCoroutineStub(channel)
    val riskEngineClient = GrpcRiskEngineClient(
        RiskCalculationServiceGrpcKt.RiskCalculationServiceCoroutineStub(channel),
        dependenciesStub,
    )
    val priceServiceBaseUrl = environment.config
        .propertyOrNull("priceService.baseUrl")?.getString() ?: "http://localhost:8082"
    val priceHttpClient = HttpClient(CIO) {
        install(ClientContentNegotiation) { json() }
    }
    val priceServiceClient = HttpPriceServiceClient(priceHttpClient, priceServiceBaseUrl)
    val ratesServiceBaseUrl = environment.config
        .propertyOrNull("ratesService.baseUrl")?.getString() ?: "http://localhost:8084"
    val ratesServiceClient = HttpRatesServiceClient(priceHttpClient, ratesServiceBaseUrl)
    val dependenciesDiscoverer = DependenciesDiscoverer(riskEngineClient)
    val marketDataFetcher = MarketDataFetcher(priceServiceClient, ratesServiceClient)

    val resultPublisher = NoOpRiskResultPublisher()
    val varCalculationService = VaRCalculationService(
        positionProvider, riskEngineClient, resultPublisher,
        dependenciesDiscoverer = dependenciesDiscoverer,
        marketDataFetcher = marketDataFetcher,
    )
    val varCache = LatestVaRCache()

    val stressTestStub = StressTestServiceGrpcKt.StressTestServiceCoroutineStub(channel)
    val regulatoryStub = RegulatoryReportingServiceGrpcKt.RegulatoryReportingServiceCoroutineStub(channel)

    module()

    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorBody("bad_request", cause.message ?: "Invalid request"),
            )
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorBody("internal_error", "An unexpected error occurred"),
            )
        }
    }

    routing {
        riskRoutes(varCalculationService, varCache, positionProvider, stressTestStub, regulatoryStub, riskEngineClient)
    }

    launch {
        ScheduledVaRCalculator(
            varCalculationService = varCalculationService,
            varCache = varCache,
            portfolioIds = { positionRepository.findDistinctPortfolioIds() },
        ).start()
    }
}
