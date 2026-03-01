package com.kinetix.regulatory.stress

import java.time.Instant

data class StressScenario(
    val id: String,
    val name: String,
    val description: String,
    val shocks: String,
    val status: ScenarioStatus,
    val createdBy: String,
    val approvedBy: String?,
    val approvedAt: Instant?,
    val createdAt: Instant,
)
