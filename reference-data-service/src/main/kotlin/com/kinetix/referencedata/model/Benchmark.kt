package com.kinetix.referencedata.model

import java.time.Instant

data class Benchmark(
    val benchmarkId: String,
    val name: String,
    val description: String?,
    val createdAt: Instant,
)
