package com.kinetix.risk.model

sealed interface ReplayResult {
    data object ManifestNotFound : ReplayResult
    data class BlobMissing(
        val manifestId: String,
        val contentHash: String,
        val dataType: String,
        val instrumentId: String,
    ) : ReplayResult
    data class Error(val message: String) : ReplayResult
    data class Success(
        val manifest: RunManifest,
        val replayResult: ValuationResult,
        val inputDigestMatch: Boolean,
        val originalInputDigest: String,
        val replayInputDigest: String,
        val originalVarValue: Double?,
        val originalExpectedShortfall: Double?,
    ) : ReplayResult
}
