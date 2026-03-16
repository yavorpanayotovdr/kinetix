package com.kinetix.common.health

import com.kinetix.common.kafka.ConsumerLivenessTracker
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe

class ReadinessCheckerTest : FunSpec({

    test("returns READY with no checks configured") {
        val checker = ReadinessChecker()
        val response = checker.check()

        response.status shouldBe "READY"
        response.checks shouldContainKey "seed"
        response.checks["seed"]!!.status shouldBe "OK"
    }

    test("returns NOT_READY when seed is not complete") {
        val checker = ReadinessChecker(seedComplete = { false })
        val response = checker.check()

        response.status shouldBe "NOT_READY"
        response.checks["seed"]!!.status shouldBe "NOT_READY"
    }

    test("includes consumer health for tracked consumers") {
        val tracker = ConsumerLivenessTracker(topic = "trades.lifecycle", groupId = "audit-group")
        tracker.recordSuccess()
        tracker.recordSuccess()
        tracker.recordDlqSend()

        val checker = ReadinessChecker(consumerTrackers = listOf(tracker))
        val response = checker.check()

        response.consumers shouldContainKey "trades.lifecycle"
        val health = response.consumers["trades.lifecycle"]!!
        health.recordsProcessedTotal shouldBe 2
        health.recordsSentToDlqTotal shouldBe 1
        health.consecutiveErrorCount shouldBe 0
    }

    test("includes extra checks in response") {
        val checker = ReadinessChecker(
            extraChecks = mapOf(
                "redis" to { CheckResult(status = "OK", details = mapOf("latencyMs" to "1")) },
            ),
        )
        val response = checker.check()

        response.status shouldBe "READY"
        response.checks shouldContainKey "redis"
        response.checks["redis"]!!.status shouldBe "OK"
    }

    test("returns NOT_READY when an extra check fails") {
        val checker = ReadinessChecker(
            extraChecks = mapOf(
                "redis" to { CheckResult(status = "ERROR", details = mapOf("error" to "connection refused")) },
            ),
        )
        val response = checker.check()

        response.status shouldBe "NOT_READY"
    }

    test("catches exceptions in extra checks and reports ERROR") {
        val checker = ReadinessChecker(
            extraChecks = mapOf(
                "broken" to { throw RuntimeException("boom") },
            ),
        )
        val response = checker.check()

        response.status shouldBe "NOT_READY"
        response.checks["broken"]!!.status shouldBe "ERROR"
        response.checks["broken"]!!.details["error"] shouldBe "boom"
    }

    test("multiple consumers are all tracked independently") {
        val tracker1 = ConsumerLivenessTracker(topic = "trades.lifecycle", groupId = "group-1")
        val tracker2 = ConsumerLivenessTracker(topic = "price.updates", groupId = "group-2")
        tracker1.recordSuccess()
        tracker2.recordError()

        val checker = ReadinessChecker(consumerTrackers = listOf(tracker1, tracker2))
        val response = checker.check()

        response.consumers.size shouldBe 2
        response.consumers["trades.lifecycle"]!!.recordsProcessedTotal shouldBe 1
        response.consumers["price.updates"]!!.consecutiveErrorCount shouldBe 1
    }
})
