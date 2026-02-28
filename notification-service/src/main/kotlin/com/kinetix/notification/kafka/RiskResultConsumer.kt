package com.kinetix.notification.kafka

import com.kinetix.common.kafka.RetryableConsumer
import com.kinetix.notification.delivery.DeliveryRouter
import com.kinetix.notification.engine.RulesEngine
import com.kinetix.notification.model.RiskResultEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.coroutines.coroutineContext

class RiskResultConsumer(
    private val consumer: KafkaConsumer<String, String>,
    private val rulesEngine: RulesEngine,
    private val deliveryRouter: DeliveryRouter,
    private val topic: String = "risk.results",
    private val retryableConsumer: RetryableConsumer = RetryableConsumer(topic = topic),
) {
    private val logger = LoggerFactory.getLogger(RiskResultConsumer::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun start() {
        withContext(Dispatchers.IO) {
            consumer.subscribe(listOf(topic))
        }
        logger.info("Subscribed to topic: {}", topic)
        while (coroutineContext.isActive) {
            val records = withContext(Dispatchers.IO) {
                consumer.poll(Duration.ofMillis(100))
            }
            for (record in records) {
                try {
                    retryableConsumer.process(record.key() ?: "", record.value()) {
                        val event = json.decodeFromString<RiskResultEvent>(record.value())
                        val alerts = rulesEngine.evaluate(event)
                        for (alert in alerts) {
                            val rule = rulesEngine.listRules().find { it.id == alert.ruleId }
                            val channels = rule?.channels ?: emptyList()
                            deliveryRouter.route(alert, channels)
                            logger.info(
                                "Alert triggered: rule={}, severity={}, portfolio={}",
                                alert.ruleName, alert.severity, alert.portfolioId,
                            )
                        }
                    }
                } catch (e: Exception) {
                    logger.error(
                        "Failed to process risk result event after retries: offset={}, partition={}",
                        record.offset(), record.partition(), e,
                    )
                }
            }
        }
    }
}
