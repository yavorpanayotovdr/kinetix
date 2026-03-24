package com.kinetix.risk.persistence

import com.kinetix.risk.model.HierarchyLevel
import com.kinetix.risk.model.HierarchyRiskSnapshot

interface RiskHierarchySnapshotRepository {
    suspend fun save(snapshot: HierarchyRiskSnapshot)
    suspend fun findLatest(level: HierarchyLevel, entityId: String): HierarchyRiskSnapshot?
    suspend fun findHistory(level: HierarchyLevel, entityId: String, limit: Int = 100): List<HierarchyRiskSnapshot>
}
