package com.kinetix.common.kafka.events

import kotlinx.serialization.Serializable

@Serializable
data class AlertLifecycleEvent(
    val alertId: String,
    val eventType: String,
    val bookId: String,
    val userId: String? = null,
    val userRole: String? = null,
    val note: String? = null,
    val occurredAt: String,
    val correlationId: String? = null,
)
