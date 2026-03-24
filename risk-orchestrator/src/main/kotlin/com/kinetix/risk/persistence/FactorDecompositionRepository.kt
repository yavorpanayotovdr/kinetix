package com.kinetix.risk.persistence

import com.kinetix.risk.model.FactorDecompositionSnapshot

interface FactorDecompositionRepository {
    suspend fun save(snapshot: FactorDecompositionSnapshot)
    suspend fun findLatestByBookId(bookId: String): FactorDecompositionSnapshot?
    suspend fun findAllByBookId(bookId: String, limit: Int = 100): List<FactorDecompositionSnapshot>
}
