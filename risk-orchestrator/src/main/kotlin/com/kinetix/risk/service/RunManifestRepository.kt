package com.kinetix.risk.service

import com.kinetix.risk.model.ManifestStatus
import com.kinetix.risk.model.MarketDataRef
import com.kinetix.risk.model.PositionSnapshotEntry
import com.kinetix.risk.model.RunManifest
import java.util.UUID

interface RunManifestRepository {
    suspend fun save(manifest: RunManifest)
    suspend fun savePositionSnapshot(manifestId: UUID, entries: List<PositionSnapshotEntry>)
    suspend fun saveMarketDataRefs(manifestId: UUID, refs: List<MarketDataRef>)
    suspend fun findByJobId(jobId: UUID): RunManifest?
    suspend fun findByManifestId(manifestId: UUID): RunManifest?
    suspend fun findPositionSnapshot(manifestId: UUID): List<PositionSnapshotEntry>
    suspend fun findMarketDataRefs(manifestId: UUID): List<MarketDataRef>
    suspend fun finaliseManifest(
        manifestId: UUID,
        modelVersion: String,
        varValue: Double?,
        expectedShortfall: Double?,
        outputDigest: String?,
        inputDigest: String,
        status: ManifestStatus,
    )
}
