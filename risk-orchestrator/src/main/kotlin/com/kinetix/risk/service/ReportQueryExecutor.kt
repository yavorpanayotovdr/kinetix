package com.kinetix.risk.service

import kotlinx.serialization.json.JsonArray

/**
 * Executes template-driven queries against the read-only reporting views.
 * Implementations must use a READ-ONLY database connection with a statement
 * timeout to prevent long-running report queries from affecting write traffic.
 */
interface ReportQueryExecutor {
    suspend fun executeRiskSummary(bookId: String, date: String?): JsonArray
    suspend fun executeStressTestSummary(bookId: String, date: String?): JsonArray
    suspend fun executePnlAttribution(bookId: String, date: String?): JsonArray
}
