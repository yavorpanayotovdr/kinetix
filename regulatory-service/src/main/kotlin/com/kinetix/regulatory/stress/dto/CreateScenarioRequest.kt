package com.kinetix.regulatory.stress.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateScenarioRequest(
    val name: String,
    val description: String,
    val shocks: String,
    val createdBy: String,
)
