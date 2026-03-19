package com.kinetix.regulatory.persistence

import com.kinetix.regulatory.model.BacktestResultRecord
import java.time.Instant

interface BacktestResultRepository {
    suspend fun save(record: BacktestResultRecord)
    suspend fun findById(id: String): BacktestResultRecord?
    suspend fun findByBookId(
        bookId: String,
        limit: Int,
        offset: Int,
        from: Instant? = null,
    ): List<BacktestResultRecord>
    suspend fun findLatestByBookId(bookId: String): BacktestResultRecord?
}
