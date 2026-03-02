package com.kinetix.position

import com.kinetix.common.kafka.RetryableConsumer
import com.kinetix.position.kafka.KafkaTradeEventPublisher
import com.kinetix.position.kafka.PriceConsumer
import com.kinetix.position.persistence.DatabaseConfig
import com.kinetix.position.persistence.DatabaseFactory
import com.kinetix.position.persistence.ExposedPositionRepository
import com.kinetix.position.persistence.ExposedTradeEventRepository
import com.kinetix.position.persistence.ExposedLimitDefinitionRepository
import com.kinetix.position.persistence.ExposedTemporaryLimitIncreaseRepository
import com.kinetix.position.routes.counterpartyRoutes
import com.kinetix.position.routes.limitRoutes
import com.kinetix.position.routes.positionRoutes
import com.kinetix.position.service.CounterpartyExposureService
import com.kinetix.position.seed.DevDataSeeder
import com.kinetix.common.model.Money
import com.kinetix.position.model.TradeLimits
import com.kinetix.position.service.ExposedTransactionalRunner
import com.kinetix.position.service.LimitCheckService
import com.kinetix.position.service.PositionQueryService
import com.kinetix.position.service.PriceUpdateService
import com.kinetix.position.service.TradeBookingService
import com.kinetix.position.service.PortfolioAggregationService
import com.kinetix.position.service.LiveFxRateProvider
import com.kinetix.position.service.StaticFxRateProvider
import com.kinetix.position.service.TradeLifecycleService
import java.math.BigDecimal
import java.util.Currency
import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import org.slf4j.event.Level
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
    log.info("Starting position-service")
    val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(MicrometerMetrics) { registry = appMicrometerRegistry }
    install(ContentNegotiation) { json() }
    install(CallLogging) {
        level = Level.INFO
        mdc("correlationId") {
            it.request.header("X-Correlation-ID") ?: java.util.UUID.randomUUID().toString()
        }
    }
    install(OpenApi) {
        info {
            title = "Position Service API"
            version = "1.0.0"
            description = "Manages portfolios, positions and trade booking"
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
    val dbConfig = environment.config.config("database")
    val db = DatabaseFactory.init(
        DatabaseConfig(
            jdbcUrl = dbConfig.property("jdbcUrl").getString(),
            username = dbConfig.property("username").getString(),
            password = dbConfig.property("password").getString(),
        )
    )

    val positionRepository = ExposedPositionRepository(db)
    val tradeEventRepository = ExposedTradeEventRepository(db)
    val transactionalRunner = ExposedTransactionalRunner(db)

    val kafkaConfig = environment.config.config("kafka")
    val bootstrapServers = kafkaConfig.property("bootstrapServers").getString()

    val producerProps = Properties().apply {
        put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
        put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
        put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
        put(ProducerConfig.ACKS_CONFIG, "all")
    }
    val kafkaProducer = KafkaProducer<String, String>(producerProps)
    val tradeEventPublisher = KafkaTradeEventPublisher(kafkaProducer)

    val defaultTradeLimits = TradeLimits(
        positionLimit = BigDecimal("1000000"),
        notionalLimit = Money(BigDecimal("10000000"), Currency.getInstance("USD")),
        concentrationLimitPct = 0.25,
    )
    val limitCheckService = LimitCheckService(positionRepository, defaultTradeLimits)

    val tradeBookingService = TradeBookingService(
        tradeEventRepository = tradeEventRepository,
        positionRepository = positionRepository,
        transactional = transactionalRunner,
        tradeEventPublisher = tradeEventPublisher,
        limitCheckService = limitCheckService,
    )
    val positionQueryService = PositionQueryService(positionRepository)
    val tradeLifecycleService = TradeLifecycleService(
        tradeEventRepository = tradeEventRepository,
        positionRepository = positionRepository,
        transactional = transactionalRunner,
        tradeEventPublisher = tradeEventPublisher,
    )

    val staticFxRateProvider = StaticFxRateProvider(
        mapOf(
            Currency.getInstance("EUR") to Currency.getInstance("USD") to BigDecimal("1.08"),
            Currency.getInstance("GBP") to Currency.getInstance("USD") to BigDecimal("1.27"),
            Currency.getInstance("JPY") to Currency.getInstance("USD") to BigDecimal("0.0067"),
            Currency.getInstance("USD") to Currency.getInstance("EUR") to BigDecimal("0.93"),
            Currency.getInstance("USD") to Currency.getInstance("GBP") to BigDecimal("0.79"),
            Currency.getInstance("USD") to Currency.getInstance("JPY") to BigDecimal("149.25"),
        )
    )
    val liveFxRateProvider = LiveFxRateProvider(delegate = staticFxRateProvider)
    val limitDefinitionRepo = ExposedLimitDefinitionRepository(db)
    val temporaryLimitIncreaseRepo = ExposedTemporaryLimitIncreaseRepository(db)
    val counterpartyExposureService = CounterpartyExposureService(tradeEventRepository)
    val portfolioAggregationService = PortfolioAggregationService(positionRepository, liveFxRateProvider)

    val priceUpdateService = PriceUpdateService(positionRepository)
    val consumerProps = Properties().apply {
        put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
        put(ConsumerConfig.GROUP_ID_CONFIG, "position-service-group")
        put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
        put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
        put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    }
    val priceKafkaConsumer = KafkaConsumer<String, String>(consumerProps)
    val retryableConsumer = RetryableConsumer(
        topic = "price.updates",
        dlqProducer = kafkaProducer,
    )
    val priceConsumer = PriceConsumer(
        priceKafkaConsumer, priceUpdateService,
        retryableConsumer = retryableConsumer,
        liveFxRateProvider = liveFxRateProvider,
    )

    module()

    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorBody("bad_request", cause.message ?: "Invalid request"),
            )
        }
        exception<IllegalStateException> { call, cause ->
            call.respond(
                HttpStatusCode.Conflict,
                ErrorBody("conflict", cause.message ?: "Invalid state"),
            )
        }
        exception<com.kinetix.position.service.InvalidTradeStateException> { call, cause ->
            call.respond(
                HttpStatusCode.Conflict,
                ErrorBody("invalid_trade_state", cause.message ?: "Invalid trade state"),
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
        positionRoutes(positionRepository, positionQueryService, tradeBookingService, tradeEventRepository, tradeLifecycleService, portfolioAggregationService)
        limitRoutes(limitDefinitionRepo, temporaryLimitIncreaseRepo)
        counterpartyRoutes(counterpartyExposureService)
    }

    launch {
        priceConsumer.start()
    }

    val seedEnabled = environment.config.propertyOrNull("seed.enabled")?.getString()?.toBoolean() ?: true
    if (seedEnabled) {
        launch {
            DevDataSeeder(tradeBookingService, positionRepository).seed()
        }
    }
}
