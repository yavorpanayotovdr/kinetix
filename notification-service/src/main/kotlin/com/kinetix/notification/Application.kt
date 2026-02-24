package com.kinetix.notification

import com.kinetix.notification.delivery.DeliveryRouter
import com.kinetix.notification.delivery.EmailDeliveryService
import com.kinetix.notification.delivery.InAppDeliveryService
import com.kinetix.notification.delivery.WebhookDeliveryService
import com.kinetix.notification.engine.RulesEngine
import com.kinetix.notification.kafka.AnomalyEventConsumer
import com.kinetix.notification.kafka.RiskResultConsumer
import com.kinetix.notification.model.AlertRule
import com.kinetix.notification.model.AlertType
import com.kinetix.notification.model.ComparisonOperator
import com.kinetix.notification.model.DeliveryChannel
import com.kinetix.notification.model.Severity
import com.kinetix.notification.persistence.DatabaseConfig
import com.kinetix.notification.persistence.DatabaseFactory
import com.kinetix.notification.persistence.ExposedAlertEventRepository
import com.kinetix.notification.persistence.ExposedAlertRuleRepository
import com.kinetix.notification.seed.DevDataSeeder
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import java.util.Properties
import java.util.UUID

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(MicrometerMetrics) { registry = appMicrometerRegistry }
    install(ContentNegotiation) { json() }
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(error = "Bad Request", message = cause.message ?: "Invalid request"),
            )
        }
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(error = "Internal Server Error", message = cause.message ?: "Unexpected error"),
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

fun Application.module(rulesEngine: RulesEngine, inAppDelivery: InAppDeliveryService) {
    module()
    routing {
        notificationRoutes(rulesEngine, inAppDelivery)
    }
}

fun Application.moduleWithRoutes() {
    val dbConfig = environment.config.config("database")
    val db = DatabaseFactory.init(
        DatabaseConfig(
            jdbcUrl = dbConfig.property("jdbcUrl").getString(),
            username = dbConfig.property("username").getString(),
            password = dbConfig.property("password").getString(),
        ),
    )

    val ruleRepository = ExposedAlertRuleRepository(db)
    val eventRepository = ExposedAlertEventRepository(db)
    val rulesEngine = RulesEngine(ruleRepository)
    val inAppDelivery = InAppDeliveryService(eventRepository)
    val emailDelivery = EmailDeliveryService()
    val webhookDelivery = WebhookDeliveryService()
    val deliveryRouter = DeliveryRouter(listOf(inAppDelivery, emailDelivery, webhookDelivery))

    module(rulesEngine, inAppDelivery)

    val kafkaConfig = environment.config.config("kafka")
    val bootstrapServers = kafkaConfig.property("bootstrapServers").getString()

    val riskConsumerProps = Properties().apply {
        put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
        put(ConsumerConfig.GROUP_ID_CONFIG, "notification-service-risk-group")
        put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
        put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
        put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    }
    val riskResultConsumer = RiskResultConsumer(
        KafkaConsumer<String, String>(riskConsumerProps),
        rulesEngine,
        deliveryRouter,
    )

    val anomalyConsumerProps = Properties().apply {
        put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
        put(ConsumerConfig.GROUP_ID_CONFIG, "notification-service-anomaly-group")
        put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
        put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
        put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    }
    val anomalyEventConsumer = AnomalyEventConsumer(
        KafkaConsumer<String, String>(anomalyConsumerProps),
    )

    launch { riskResultConsumer.start() }
    launch { anomalyEventConsumer.start() }

    val seedEnabled = environment.config.propertyOrNull("seed.enabled")?.getString()?.toBoolean() ?: true
    if (seedEnabled) {
        launch {
            DevDataSeeder(rulesEngine, eventRepository).seed()
        }
    }
}

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String,
)

@Serializable
data class CreateAlertRuleRequest(
    val name: String,
    val type: String,
    val threshold: Double,
    val operator: String,
    val severity: String,
    val channels: List<String>,
)

@Serializable
data class AlertRuleResponse(
    val id: String,
    val name: String,
    val type: String,
    val threshold: Double,
    val operator: String,
    val severity: String,
    val channels: List<String>,
    val enabled: Boolean,
)

@Serializable
data class AlertEventResponse(
    val id: String,
    val ruleId: String,
    val ruleName: String,
    val type: String,
    val severity: String,
    val message: String,
    val currentValue: Double,
    val threshold: Double,
    val portfolioId: String,
    val triggeredAt: String,
)

fun Route.notificationRoutes(rulesEngine: RulesEngine, inAppDelivery: InAppDeliveryService) {
    route("/api/v1/notifications") {
        get("/rules") {
            val rules = rulesEngine.listRules().map { it.toResponse() }
            call.respond(rules)
        }

        post("/rules") {
            val request = call.receive<CreateAlertRuleRequest>()
            require(request.name.isNotBlank()) { "Rule name must not be blank" }
            require(request.channels.isNotEmpty()) { "At least one delivery channel is required" }
            val rule = AlertRule(
                id = UUID.randomUUID().toString(),
                name = request.name,
                type = AlertType.valueOf(request.type),
                threshold = request.threshold,
                operator = ComparisonOperator.valueOf(request.operator),
                severity = Severity.valueOf(request.severity),
                channels = request.channels.map { DeliveryChannel.valueOf(it) },
            )
            rulesEngine.addRule(rule)
            call.respond(HttpStatusCode.Created, rule.toResponse())
        }

        delete("/rules/{ruleId}") {
            val ruleId = call.parameters["ruleId"]
                ?: throw IllegalArgumentException("Missing required path parameter: ruleId")
            val exists = rulesEngine.listRules().any { it.id == ruleId }
            if (exists) {
                rulesEngine.removeRule(ruleId)
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        get("/alerts") {
            val limit = call.queryParameters["limit"]?.toIntOrNull() ?: 50
            val alerts = inAppDelivery.getRecentAlerts(limit).map { it.toEventResponse() }
            call.respond(alerts)
        }
    }
}

private fun AlertRule.toResponse() = AlertRuleResponse(
    id = id,
    name = name,
    type = type.name,
    threshold = threshold,
    operator = operator.name,
    severity = severity.name,
    channels = channels.map { it.name },
    enabled = enabled,
)

private fun com.kinetix.notification.model.AlertEvent.toEventResponse() = AlertEventResponse(
    id = id,
    ruleId = ruleId,
    ruleName = ruleName,
    type = type.name,
    severity = severity.name,
    message = message,
    currentValue = currentValue,
    threshold = threshold,
    portfolioId = portfolioId,
    triggeredAt = triggeredAt.toString(),
)
