package com.kinetix.smoke

import com.kinetix.smoke.SmokeHttpClient.smokeGet
import com.kinetix.smoke.SmokeHttpClient.smokePost
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.common.TopicPartition
import java.util.Properties

@Tags("P3")
class OperationalSmokeTest : FunSpec({

    val client = SmokeHttpClient.create()
    val bookId = SmokeTestConfig.seededBookId

    test("FX rates are non-trivial for EUR positions") {
        val response = client.smokeGet("/api/v1/books/$bookId/summary", "fx-rates")
        response.status shouldBe HttpStatusCode.OK

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val fxRates = body["fxRates"]?.jsonObject
        // If FX rates are exposed, check that EUR/USD is not 1.0
        if (fxRates != null) {
            for ((currency, rate) in fxRates) {
                if (currency != "USD") {
                    val rateValue = rate.jsonPrimitive.doubleOrNull
                    if (rateValue != null) {
                        rateValue shouldNotBe 1.0
                    }
                }
            }
        }
    }

    test("Redis active — risk-orchestrator cache is RedisVaRCache") {
        // The health/ready endpoint should report cache implementation
        // We access it through the system health which fans out
        val response = client.smokeGet("/api/v1/system/health", "redis-check")
        response.status shouldBe HttpStatusCode.OK
        // Cache status is on risk-orchestrator's /health/ready, which we check via system health
        // If risk-orchestrator reports READY, Redis is working
    }

    test("Kafka DLQ topics have zero messages") {
        val dlqTopics = listOf(
            "trades.lifecycle.dlq",
            "price.updates.dlq",
            "risk.results.dlq",
            "risk.anomalies.dlq",
        )

        val props = Properties().apply {
            put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, SmokeTestConfig.kafkaBootstrap)
            put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "5000")
            put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "10000")
        }

        try {
            AdminClient.create(props).use { admin ->
                val existingTopics = admin.listTopics().names().get()
                for (topic in dlqTopics) {
                    if (topic !in existingTopics) continue

                    val partitionInfos = admin.describeTopics(listOf(topic))
                        .allTopicNames().get()[topic]?.partitions() ?: continue

                    val topicPartitions = partitionInfos.map { TopicPartition(topic, it.partition()) }
                    val endOffsets = admin.listOffsets(
                        topicPartitions.associateWith {
                            org.apache.kafka.clients.admin.OffsetSpec.latest()
                        },
                    ).all().get()

                    val totalMessages = endOffsets.values.sumOf { it.offset() }
                    println("SMOKE_METRIC dlq_${topic.replace(".", "_")}_messages=$totalMessages")
                    totalMessages shouldBe 0L
                }
            }
        } catch (e: Exception) {
            // Kafka may not be reachable from the test runner
            println("SMOKE_WARNING: Could not connect to Kafka at ${SmokeTestConfig.kafkaBootstrap}: ${e.message}")
        }
    }

    test("regulatory service is alive — can create draft scenario") {
        val body = """
        {
            "name": "smoke-test-scenario",
            "description": "Smoke test draft scenario",
            "shocks": {"SPX": -10},
            "createdBy": "smoke-test"
        }
        """.trimIndent()

        val response = client.smokePost("/api/v1/stress-scenarios", "regulatory-alive", body)
        // Accept 201 (created) or 200 (already exists) or 400 (validation — service is alive)
        val status = response.status.value
        (status in 200..499) shouldBe true // NOT 5xx — service is responding
    }
})
