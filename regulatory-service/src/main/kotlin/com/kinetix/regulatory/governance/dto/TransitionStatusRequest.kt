package com.kinetix.regulatory.governance.dto

import kotlinx.serialization.Serializable

@Serializable
data class TransitionStatusRequest(
    val targetStatus: String,
    val approvedBy: String? = null,
)
