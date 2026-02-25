package com.kinetix.audit

import com.kinetix.audit.dto.ErrorResponse
import com.kinetix.audit.kafka.AuditEventConsumer
import com.kinetix.audit.persistence.AuditEventRepository
import com.kinetix.audit.persistence.DatabaseConfig
import com.kinetix.audit.persistence.DatabaseFactory
import com.kinetix.audit.persistence.ExposedAuditEventRepository
import com.kinetix.audit.routes.auditRoutes
import com.kinetix.audit.seed.DevDataSeeder
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
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import java.util.Properties

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(MicrometerMetrics) { registry = appMicrometerRegistry }
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
        })
    }
    install(OpenApi) {
        info {
            title = "Audit Service API"
            version = "1.0.0"
            description = "Records and queries audit events"
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

fun Application.module(repository: AuditEventRepository) {
    module()
    routing {
        auditRoutes(repository)
    }
}

fun Application.moduleWithRoutes() {
    val dbConfig = environment.config.config("database")
    val db = DatabaseFactory.init(
        DatabaseConfig(
            jdbcUrl = dbConfig.property("jdbcUrl").getString(),
            username = dbConfig.property("username").getString(),
            password = dbConfig.property("password").getString(),
        )
    )
    val repository = ExposedAuditEventRepository(db)
    module(repository)

    val kafkaConfig = environment.config.config("kafka")
    val bootstrapServers = kafkaConfig.property("bootstrapServers").getString()
    val consumerProps = Properties().apply {
        put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
        put(ConsumerConfig.GROUP_ID_CONFIG, "audit-service-group")
        put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
        put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
        put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    }
    val kafkaConsumer = KafkaConsumer<String, String>(consumerProps)
    val auditEventConsumer = AuditEventConsumer(kafkaConsumer, repository)

    launch {
        auditEventConsumer.start()
    }

    val seedEnabled = environment.config.propertyOrNull("seed.enabled")?.getString()?.toBoolean() ?: true
    if (seedEnabled) {
        launch {
            DevDataSeeder(repository).seed()
        }
    }
}
