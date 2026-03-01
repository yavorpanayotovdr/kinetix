package com.kinetix.risk

import com.kinetix.common.kafka.RetryableConsumer
import com.kinetix.common.resilience.CircuitBreaker
import com.kinetix.proto.risk.MarketDataDependenciesServiceGrpcKt
import com.kinetix.proto.risk.RiskCalculationServiceGrpcKt
import com.kinetix.proto.risk.RegulatoryReportingServiceGrpcKt
import com.kinetix.proto.risk.StressTestServiceGrpcKt
import com.kinetix.risk.cache.InMemoryVaRCache
import com.kinetix.risk.cache.RedisVaRCache
import com.kinetix.risk.cache.VaRCache
import com.kinetix.risk.client.GrpcRiskEngineClient
import com.kinetix.risk.client.HttpPositionServiceClient
import com.kinetix.risk.client.HttpPriceServiceClient
import com.kinetix.risk.client.HttpRatesServiceClient
import com.kinetix.risk.client.HttpReferenceDataServiceClient
import com.kinetix.risk.client.HttpCorrelationServiceClient
import com.kinetix.risk.client.HttpVolatilityServiceClient
import com.kinetix.risk.client.PositionServicePositionProvider
import com.kinetix.risk.client.ResilientRiskEngineClient
import com.kinetix.risk.kafka.KafkaRiskResultPublisher
import com.kinetix.risk.kafka.PriceEventConsumer
import com.kinetix.risk.kafka.TradeEventConsumer
import com.kinetix.risk.persistence.ExposedDailyRiskSnapshotRepository
import com.kinetix.risk.persistence.ExposedSodBaselineRepository
import com.kinetix.risk.persistence.ExposedValuationJobRecorder
import com.kinetix.risk.persistence.RiskDatabaseConfig
import com.kinetix.risk.persistence.RiskDatabaseFactory
import com.kinetix.risk.routes.riskRoutes
import com.kinetix.risk.routes.jobHistoryRoutes
import com.kinetix.risk.schedule.ScheduledSodSnapshotJob
import com.kinetix.risk.schedule.ScheduledVaRCalculator
import com.kinetix.risk.service.DependenciesDiscoverer
import com.kinetix.risk.service.MarketDataFetcher
import com.kinetix.risk.service.PnlAttributionService
import com.kinetix.risk.service.PnlComputationService
import com.kinetix.risk.service.SodSnapshotService
import com.kinetix.risk.service.VaRCalculationService
import com.kinetix.risk.simulation.*
import io.lettuce.core.RedisClient
import io.grpc.ManagedChannelBuilder
import io.grpc.TlsChannelCredentials
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.serialization.kotlinx.json.*
import java.io.File
import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktorswaggerui.swaggerUI
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
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import java.util.Properties

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(MicrometerMetrics) { registry = appMicrometerRegistry }
    install(ContentNegotiation) { json() }
    install(OpenApi) {
        info {
            title = "Risk Orchestrator API"
            version = "1.0.0"
            description = "Orchestrates VaR calculations, stress tests, Greeks and regulatory reporting"
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

@Serializable
private data class ErrorBody(val error: String, val message: String)

fun Application.moduleWithRoutes() {
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

    val dependenciesStub = MarketDataDependenciesServiceGrpcKt.MarketDataDependenciesServiceCoroutineStub(channel)
    val grpcRiskEngineClient = GrpcRiskEngineClient(
        RiskCalculationServiceGrpcKt.RiskCalculationServiceCoroutineStub(channel),
        dependenciesStub,
    )
    val riskEngineClient = ResilientRiskEngineClient(grpcRiskEngineClient, CircuitBreaker())

    val priceServiceBaseUrl = environment.config
        .propertyOrNull("priceService.baseUrl")?.getString() ?: "http://localhost:8082"
    val priceHttpClient = HttpClient(CIO) {
        install(ClientContentNegotiation) { json() }
    }
    val priceServiceClient = HttpPriceServiceClient(priceHttpClient, priceServiceBaseUrl)
    val ratesServiceBaseUrl = environment.config
        .propertyOrNull("ratesService.baseUrl")?.getString() ?: "http://localhost:8088"
    val ratesServiceClient = HttpRatesServiceClient(priceHttpClient, ratesServiceBaseUrl)
    val referenceDataServiceBaseUrl = environment.config
        .propertyOrNull("referenceDataService.baseUrl")?.getString() ?: "http://localhost:8089"
    val referenceDataServiceClient = HttpReferenceDataServiceClient(priceHttpClient, referenceDataServiceBaseUrl)
    val volatilityServiceBaseUrl = environment.config
        .propertyOrNull("volatilityService.baseUrl")?.getString() ?: "http://localhost:8090"
    val volatilityServiceClient = HttpVolatilityServiceClient(priceHttpClient, volatilityServiceBaseUrl)
    val correlationServiceBaseUrl = environment.config
        .propertyOrNull("correlationService.baseUrl")?.getString() ?: "http://localhost:8091"
    val correlationServiceClient = HttpCorrelationServiceClient(priceHttpClient, correlationServiceBaseUrl)
    val positionServiceBaseUrl = environment.config
        .propertyOrNull("positionService.baseUrl")?.getString() ?: "http://localhost:8081"
    val positionServiceClient = HttpPositionServiceClient(priceHttpClient, positionServiceBaseUrl)
    val positionProvider = PositionServicePositionProvider(positionServiceClient)

    val simulationDelays = SimulationDelays.from(environment.config)
    if (simulationDelays != null) {
        log.info("Simulation delays ENABLED: $simulationDelays")
    }

    val effectivePositionProvider = simulationDelays?.let {
        DelayingPositionProvider(positionProvider, it.fetchPositionsMs)
    } ?: positionProvider

    val effectiveRiskEngineClient = simulationDelays?.let {
        DelayingRiskEngineClient(riskEngineClient, it.discoverDependenciesMs, it.calculateVaRMs)
    } ?: riskEngineClient

    val effectivePriceServiceClient = simulationDelays?.let {
        DelayingPriceServiceClient(priceServiceClient, it.fetchMarketDataPerCallMs)
    } ?: priceServiceClient

    val effectiveRatesServiceClient = simulationDelays?.let {
        DelayingRatesServiceClient(ratesServiceClient, it.fetchMarketDataPerCallMs)
    } ?: ratesServiceClient

    val effectiveReferenceDataServiceClient = simulationDelays?.let {
        DelayingReferenceDataServiceClient(referenceDataServiceClient, it.fetchMarketDataPerCallMs)
    } ?: referenceDataServiceClient

    val effectiveVolatilityServiceClient = simulationDelays?.let {
        DelayingVolatilityServiceClient(volatilityServiceClient, it.fetchMarketDataPerCallMs)
    } ?: volatilityServiceClient

    val effectiveCorrelationServiceClient = simulationDelays?.let {
        DelayingCorrelationServiceClient(correlationServiceClient, it.fetchMarketDataPerCallMs)
    } ?: correlationServiceClient

    val dependenciesDiscoverer = DependenciesDiscoverer(effectiveRiskEngineClient)
    val marketDataFetcher = MarketDataFetcher(
        effectivePriceServiceClient, effectiveRatesServiceClient, effectiveReferenceDataServiceClient,
        effectiveVolatilityServiceClient, effectiveCorrelationServiceClient,
        priceServiceBaseUrl = priceServiceBaseUrl,
        ratesServiceBaseUrl = ratesServiceBaseUrl,
        referenceDataServiceBaseUrl = referenceDataServiceBaseUrl,
        volatilityServiceBaseUrl = volatilityServiceBaseUrl,
        correlationServiceBaseUrl = correlationServiceBaseUrl,
    )

    val riskDbConfig = environment.config.config("riskDatabase")
    val riskDb = RiskDatabaseFactory.init(
        RiskDatabaseConfig(
            jdbcUrl = riskDbConfig.property("jdbcUrl").getString(),
            username = riskDbConfig.property("username").getString(),
            password = riskDbConfig.property("password").getString(),
        )
    )
    val jobRecorder = ExposedValuationJobRecorder(riskDb)
    val pnlAttributionRepository = com.kinetix.risk.persistence.ExposedPnlAttributionRepository(riskDb)
    val dailyRiskSnapshotRepository = ExposedDailyRiskSnapshotRepository(riskDb)
    val sodBaselineRepository = ExposedSodBaselineRepository(riskDb)

    val kafkaConfig = environment.config.config("kafka")
    val bootstrapServers = kafkaConfig.property("bootstrapServers").getString()

    val producerProps = Properties().apply {
        put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
        put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
        put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
        put(ProducerConfig.ACKS_CONFIG, "all")
    }
    val kafkaProducer = KafkaProducer<String, String>(producerProps)
    val resultPublisher = KafkaRiskResultPublisher(kafkaProducer)

    val varCalculationService = VaRCalculationService(
        effectivePositionProvider, effectiveRiskEngineClient, resultPublisher,
        dependenciesDiscoverer = dependenciesDiscoverer,
        marketDataFetcher = marketDataFetcher,
        jobRecorder = jobRecorder,
    )
    val varCache: VaRCache = run {
        val redisUrl = environment.config.propertyOrNull("redis.url")?.getString().orEmpty()
        if (redisUrl.isNotBlank()) {
            val ttl = environment.config.propertyOrNull("redis.ttlSeconds")?.getString()?.toLongOrNull() ?: 300L
            val client = RedisClient.create(redisUrl)
            val connection = client.connect()
            log.info("Using RedisVaRCache at {}", redisUrl)
            RedisVaRCache(connection, ttl)
        } else {
            log.info("No REDIS_URL configured, using InMemoryVaRCache")
            InMemoryVaRCache()
        }
    }

    val sodSnapshotService = SodSnapshotService(
        sodBaselineRepository = sodBaselineRepository,
        dailyRiskSnapshotRepository = dailyRiskSnapshotRepository,
        varCache = varCache,
        varCalculationService = varCalculationService,
        positionProvider = effectivePositionProvider,
        jobRecorder = jobRecorder,
    )
    val pnlAttributionService = PnlAttributionService()
    val pnlComputationService = PnlComputationService(
        sodSnapshotService = sodSnapshotService,
        dailyRiskSnapshotRepository = dailyRiskSnapshotRepository,
        pnlAttributionService = pnlAttributionService,
        pnlAttributionRepository = pnlAttributionRepository,
        varCache = varCache,
        positionProvider = effectivePositionProvider,
    )

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
        riskRoutes(varCalculationService, varCache, effectivePositionProvider, stressTestStub, regulatoryStub, effectiveRiskEngineClient, pnlAttributionRepository = pnlAttributionRepository, sodSnapshotService = sodSnapshotService, pnlComputationService = pnlComputationService)
        jobHistoryRoutes(jobRecorder)
    }

    val tradeConsumerProps = Properties().apply {
        put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
        put(ConsumerConfig.GROUP_ID_CONFIG, "risk-orchestrator-trades-group")
        put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
        put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
        put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    }
    val tradeRetryableConsumer = RetryableConsumer(
        topic = "trades.lifecycle",
        dlqProducer = kafkaProducer,
    )
    val tradeEventConsumer = TradeEventConsumer(
        KafkaConsumer<String, String>(tradeConsumerProps),
        varCalculationService,
        retryableConsumer = tradeRetryableConsumer,
    )

    val priceConsumerProps = Properties().apply {
        put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
        put(ConsumerConfig.GROUP_ID_CONFIG, "risk-orchestrator-prices-group")
        put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
        put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
        put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    }
    val priceRetryableConsumer = RetryableConsumer(
        topic = "price.updates",
        dlqProducer = kafkaProducer,
    )
    val priceEventConsumer = PriceEventConsumer(
        KafkaConsumer<String, String>(priceConsumerProps),
        varCalculationService,
        affectedPortfolios = { when (val r = positionServiceClient.getDistinctPortfolioIds()) {
                is com.kinetix.risk.client.ClientResponse.Success -> r.value
                is com.kinetix.risk.client.ClientResponse.NotFound -> emptyList()
            } },
        retryableConsumer = priceRetryableConsumer,
    )

    launch { tradeEventConsumer.start() }
    launch { priceEventConsumer.start() }
    launch {
        ScheduledVaRCalculator(
            varCalculationService = varCalculationService,
            varCache = varCache,
            portfolioIds = { when (val r = positionServiceClient.getDistinctPortfolioIds()) {
                is com.kinetix.risk.client.ClientResponse.Success -> r.value
                is com.kinetix.risk.client.ClientResponse.NotFound -> emptyList()
            } },
        ).start()
    }
    launch {
        ScheduledSodSnapshotJob(
            sodSnapshotService = sodSnapshotService,
            portfolioIds = { when (val r = positionServiceClient.getDistinctPortfolioIds()) {
                is com.kinetix.risk.client.ClientResponse.Success -> r.value
                is com.kinetix.risk.client.ClientResponse.NotFound -> emptyList()
            } },
        ).start()
    }
}
