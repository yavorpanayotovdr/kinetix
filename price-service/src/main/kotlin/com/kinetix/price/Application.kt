package com.kinetix.price

import com.kinetix.price.cache.RedisPriceCache
import com.kinetix.price.kafka.KafkaPricePublisher
import com.kinetix.price.metrics.PriceMetrics
import com.kinetix.price.metrics.PriceStalenessTracker
import com.kinetix.price.persistence.DatabaseConfig
import com.kinetix.price.persistence.DatabaseFactory
import com.kinetix.price.persistence.ExposedPriceRepository
import com.kinetix.price.persistence.PriceRepository
import com.kinetix.price.routes.priceRoutes
import com.kinetix.price.feed.InstrumentSeed
import com.kinetix.price.feed.PriceFeedSimulator
import com.kinetix.price.seed.DevDataSeeder
import com.kinetix.price.service.PriceIngestionService
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
import io.ktor.server.response.*
import org.slf4j.event.Level
import io.ktor.server.routing.*
import io.lettuce.core.RedisClient
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import com.kinetix.common.model.PriceSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import java.util.Properties

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module(
    appMicrometerRegistry: PrometheusMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT),
) {
    log.info("Starting price-service")
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
            title = "Price Service API"
            version = "1.0.0"
            description = "Manages instrument prices, price history and ingestion"
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

fun Application.module(repository: PriceRepository, ingestionService: PriceIngestionService) {
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
        priceRoutes(repository, ingestionService)
    }
}

@Serializable
private data class ErrorBody(val error: String, val message: String)

fun Application.moduleWithRoutes() {
    val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    val dbConfig = environment.config.config("database")
    val db = DatabaseFactory.init(
        DatabaseConfig(
            jdbcUrl = dbConfig.property("jdbcUrl").getString(),
            username = dbConfig.property("username").getString(),
            password = dbConfig.property("password").getString(),
        )
    )
    val repository = ExposedPriceRepository(db)

    val redisConfig = environment.config.config("redis")
    val redisUrl = redisConfig.property("url").getString()
    val redisClient = RedisClient.create(redisUrl)
    val redisConnection = redisClient.connect()
    val cache = RedisPriceCache(redisConnection)

    val kafkaConfig = environment.config.config("kafka")
    val bootstrapServers = kafkaConfig.property("bootstrapServers").getString()
    val producerProps = Properties().apply {
        put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
        put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
        put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
        put(ProducerConfig.ACKS_CONFIG, "all")
    }
    val kafkaProducer = KafkaProducer<String, String>(producerProps)
    val publisher = KafkaPricePublisher(kafkaProducer)

    val stalenessTracker = PriceStalenessTracker(appMicrometerRegistry)
    val priceMetrics = PriceMetrics(appMicrometerRegistry)
    val ingestionService = PriceIngestionService(repository, cache, publisher, stalenessTracker, priceMetrics)

    module(appMicrometerRegistry)
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
        priceRoutes(repository, ingestionService)
    }

    val seedEnabled = environment.config.propertyOrNull("seed.enabled")?.getString()?.toBoolean() ?: true
    if (seedEnabled) {
        launch {
            DevDataSeeder(repository).seed()
        }
    }

    val feedEnabled = environment.config.propertyOrNull("feed.enabled")?.getString()?.toBoolean() ?: true
    if (feedEnabled) {
        val seeds = DevDataSeeder.INSTRUMENTS.map { (id, config) ->
            InstrumentSeed(
                instrumentId = id,
                initialPrice = BigDecimal(config.latestPrice.toString()),
                currency = Currency.getInstance(config.currency),
                assetClass = config.assetClass,
            )
        }
        val simulator = PriceFeedSimulator(seeds)
        launch {
            while (isActive) {
                delay(1000)
                try {
                    simulator.tick(Instant.now(), PriceSource.INTERNAL).forEach { point ->
                        ingestionService.ingest(point)
                    }
                } catch (e: Exception) {
                    log.error("Price feed simulator tick failed", e)
                }
            }
        }
    }
}
