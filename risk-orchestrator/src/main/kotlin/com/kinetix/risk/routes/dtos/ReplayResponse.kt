package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class ReplayResponse(
    val manifest: RunManifestResponse,
    val replayVarValue: Double?,
    val replayExpectedShortfall: Double?,
    val replayModelVersion: String?,
    val inputDigestMatch: Boolean,
    val originalInputDigest: String,
    val replayInputDigest: String,
    val originalVarValue: Double?,
    val originalExpectedShortfall: Double?,
)
