package com.kinetix.correlation.cache

import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import org.testcontainers.containers.GenericContainer

object RedisTestSetup {

    private val redis = GenericContainer("redis:7-alpine")
        .withExposedPorts(6379)

    fun start(): StatefulRedisConnection<String, String> {
        if (!redis.isRunning) {
            redis.start()
        }
        val redisUri = "redis://${redis.host}:${redis.getMappedPort(6379)}"
        val client = RedisClient.create(redisUri)
        return client.connect()
    }
}
