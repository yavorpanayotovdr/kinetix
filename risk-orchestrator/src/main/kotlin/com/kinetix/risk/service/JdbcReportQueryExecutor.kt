package com.kinetix.risk.service

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.ResultSet
import javax.sql.DataSource

private const val STATEMENT_TIMEOUT_MS = 30_000

/**
 * Executes parameterised, read-only SQL queries against the reporting views.
 *
 * Uses a separate read-only DataSource so reports cannot accidentally mutate
 * production data, and enforces a 30-second statement timeout to prevent
 * long-running report queries from blocking connection pool slots.
 *
 * Query parameters (bookId, date) are always bound via PreparedStatement to
 * prevent any possibility of SQL injection.
 */
class JdbcReportQueryExecutor(private val readOnlyDataSource: DataSource) : ReportQueryExecutor {

    private val logger = LoggerFactory.getLogger(JdbcReportQueryExecutor::class.java)

    override suspend fun executeRiskSummary(bookId: String, date: String?): JsonArray {
        val sql = """
            SELECT
                book_id, instrument_id, asset_class,
                quantity, market_price,
                delta, gamma, vega, theta, rho,
                var_contribution, es_contribution,
                snapshot_date
            FROM risk_positions_flat
            WHERE book_id = ?
            ORDER BY var_contribution DESC NULLS LAST
        """.trimIndent()
        return executeQuery(sql, listOf(bookId))
    }

    override suspend fun executeStressTestSummary(bookId: String, date: String?): JsonArray {
        // stress_test_results is not a TimescaleDB hypertable — plain table query.
        val sql = buildString {
            append("""
                SELECT
                    book_id, scenario_name,
                    base_var, stressed_var, pnl_impact,
                    calculated_at
                FROM stress_test_results
                WHERE book_id = ?
            """.trimIndent())
            if (date != null) {
                append(" AND DATE(calculated_at) = ?")
            }
            append(" ORDER BY pnl_impact ASC")
        }
        val params = if (date != null) listOf(bookId, date) else listOf(bookId)
        return executeQuery(sql, params)
    }

    override suspend fun executePnlAttribution(bookId: String, date: String?): JsonArray {
        val sql = buildString {
            append("""
                SELECT
                    book_id, attribution_date,
                    total_pnl, delta_pnl, gamma_pnl, vega_pnl,
                    theta_pnl, rho_pnl, unexplained_pnl
                FROM pnl_attributions
                WHERE book_id = ?
            """.trimIndent())
            if (date != null) {
                append(" AND attribution_date = ?::date")
            }
            append(" ORDER BY attribution_date DESC")
        }
        val params = if (date != null) listOf(bookId, date) else listOf(bookId)
        return executeQuery(sql, params)
    }

    private fun executeQuery(sql: String, params: List<String>): JsonArray {
        readOnlyDataSource.connection.use { conn ->
            conn.isReadOnly = true
            conn.createStatement().use { stmt ->
                stmt.queryTimeout = STATEMENT_TIMEOUT_MS / 1_000
            }
            conn.prepareStatement(sql).use { ps ->
                params.forEachIndexed { i, param -> ps.setString(i + 1, param) }
                ps.queryTimeout = STATEMENT_TIMEOUT_MS / 1_000
                ps.executeQuery().use { rs ->
                    return rs.toJsonArray()
                }
            }
        }
    }

    private fun ResultSet.toJsonArray(): JsonArray {
        val meta = metaData
        val columnCount = meta.columnCount
        val columns = (1..columnCount).map { meta.getColumnLabel(it) }
        return buildJsonArray {
            while (next()) {
                add(buildJsonObject {
                    for ((index, name) in columns.withIndex()) {
                        val value = getString(index + 1) ?: continue
                        put(name, value)
                    }
                })
            }
        }
    }
}
