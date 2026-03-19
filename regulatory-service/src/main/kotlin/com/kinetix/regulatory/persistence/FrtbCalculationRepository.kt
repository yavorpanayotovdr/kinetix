package com.kinetix.regulatory.persistence

import com.kinetix.regulatory.model.FrtbCalculationRecord
import java.time.Instant

interface FrtbCalculationRepository {
    suspend fun save(record: FrtbCalculationRecord)
    suspend fun findByBookId(
        bookId: String,
        limit: Int,
        offset: Int,
        from: Instant? = null,
    ): List<FrtbCalculationRecord>
    suspend fun findLatestByBookId(bookId: String): FrtbCalculationRecord?
}
