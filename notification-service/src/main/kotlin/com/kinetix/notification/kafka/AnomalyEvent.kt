package com.kinetix.notification.kafka

import kotlinx.serialization.Serializable

@Serializable
data class AnomalyEvent(
    val metricName: String,
    val isAnomaly: Boolean,
    val anomalyScore: Double,
    val explanation: String,
    val detectedAt: String,
)
