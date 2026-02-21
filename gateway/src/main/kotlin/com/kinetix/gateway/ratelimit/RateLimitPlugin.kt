package com.kinetix.gateway.ratelimit

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

val RateLimit = createApplicationPlugin("RateLimit", ::RateLimitPluginConfig) {
    val rateLimiter = pluginConfig.rateLimiter
    val excludedPaths = pluginConfig.excludedPaths

    onCall { call ->
        val path = call.request.local.uri
        if (path in excludedPaths) return@onCall

        val clientId = call.request.local.remoteAddress
        if (!rateLimiter.tryAcquire(clientId)) {
            call.response.header("Retry-After", "1")
            call.respond(HttpStatusCode.TooManyRequests, mapOf("error" to "rate_limited", "message" to "Too many requests"))
        }
    }
}

class RateLimitPluginConfig {
    var rateLimiter: TokenBucketRateLimiter = TokenBucketRateLimiter()
    var excludedPaths: Set<String> = setOf("/health", "/metrics")
}
