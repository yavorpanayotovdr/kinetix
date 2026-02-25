package com.kinetix.common.persistence

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class ConnectionPoolConfigTest : FunSpec({

    test("has sensible defaults") {
        val config = ConnectionPoolConfig()
        config.maxPoolSize shouldBe 10
        config.minIdle shouldBe 2
        config.connectionTimeoutMs shouldBe 30_000
        config.idleTimeoutMs shouldBe 600_000
        config.maxLifetimeMs shouldBe 1_800_000
        config.leakDetectionThresholdMs shouldBe 60_000
        config.transactionIsolation shouldBe "TRANSACTION_REPEATABLE_READ"
        config.autoCommit shouldBe false
    }

    test("validates maxPoolSize > 0") {
        val ex = shouldThrow<IllegalArgumentException> {
            ConnectionPoolConfig(maxPoolSize = 0)
        }
        ex.message shouldContain "maxPoolSize"
    }

    test("validates minIdle <= maxPoolSize") {
        val ex = shouldThrow<IllegalArgumentException> {
            ConnectionPoolConfig(maxPoolSize = 5, minIdle = 10)
        }
        ex.message shouldContain "minIdle"
    }

    test("validates connectionTimeout > 0") {
        val ex = shouldThrow<IllegalArgumentException> {
            ConnectionPoolConfig(connectionTimeoutMs = 0)
        }
        ex.message shouldContain "connectionTimeoutMs"
    }

    test("forService returns tuned config per service") {
        val posConfig = ConnectionPoolConfig.forService("position-service")
        posConfig.maxPoolSize shouldBe 15
        posConfig.minIdle shouldBe 3

        val auditConfig = ConnectionPoolConfig.forService("audit-service")
        auditConfig.maxPoolSize shouldBe 8
        auditConfig.minIdle shouldBe 2

        val mdConfig = ConnectionPoolConfig.forService("price-service")
        mdConfig.maxPoolSize shouldBe 20
        mdConfig.minIdle shouldBe 5

        val defaultConfig = ConnectionPoolConfig.forService("unknown-service")
        defaultConfig.maxPoolSize shouldBe 10
    }

    test("dev mode sets minIdle to 0 and reduces connection timeout") {
        val config = ConnectionPoolConfig.forService("position-service", devMode = true)
        config.minIdle shouldBe 0
        config.connectionTimeoutMs shouldBe 5_000
    }

    test("dev mode preserves service-specific maxPoolSize") {
        val priceConfig = ConnectionPoolConfig.forService("price-service", devMode = true)
        priceConfig.maxPoolSize shouldBe 20

        val posConfig = ConnectionPoolConfig.forService("position-service", devMode = true)
        posConfig.maxPoolSize shouldBe 15
    }

    test("dev mode applies to default config") {
        val config = ConnectionPoolConfig.forService("unknown-service", devMode = true)
        config.minIdle shouldBe 0
        config.connectionTimeoutMs shouldBe 5_000
        config.maxPoolSize shouldBe 10
    }

    test("non-dev mode preserves original config") {
        val config = ConnectionPoolConfig.forService("position-service", devMode = false)
        config.minIdle shouldBe 3
        config.connectionTimeoutMs shouldBe 30_000
    }
})
