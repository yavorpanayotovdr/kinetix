package com.kinetix.risk.model

import java.time.Instant

data class FetchFailure(
    override val dependency: DiscoveredDependency,
    val reason: String,
    val url: String?,
    val httpStatus: Int?,
    val errorMessage: String?,
    val service: String,
    val timestamp: Instant,
    val durationMs: Long,
) : FetchResult
