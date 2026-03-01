package com.kinetix.regulatory.stress.dto

import kotlinx.serialization.Serializable

@Serializable
data class ApproveScenarioRequest(
    val approvedBy: String,
)
