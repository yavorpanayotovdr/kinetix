package com.kinetix.common.model

import java.time.Instant

data class CorrelationMatrix(
    val labels: List<String>,
    val values: List<Double>,
    val windowDays: Int,
    val asOfDate: Instant,
    val method: EstimationMethod,
) {
    init {
        require(labels.isNotEmpty()) { "Labels must not be empty" }
        require(values.size == labels.size * labels.size) {
            "Values size (${values.size}) must be labels.size^2 (${labels.size * labels.size})"
        }
    }
}
