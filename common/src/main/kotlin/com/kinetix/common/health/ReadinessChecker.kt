package com.kinetix.common.health

import com.kinetix.common.kafka.ConsumerLivenessTracker
import org.slf4j.LoggerFactory
import java.sql.Connection
import javax.sql.DataSource

class ReadinessChecker(
    private val dataSource: DataSource? = null,
    private val flywayLocation: String? = null,
    private val consumerTrackers: List<ConsumerLivenessTracker> = emptyList(),
    private val extraChecks: Map<String, () -> CheckResult> = emptyMap(),
    private val seedComplete: () -> Boolean = { true },
) {
    private val logger = LoggerFactory.getLogger(ReadinessChecker::class.java)

    fun check(): ReadinessResponse {
        val checks = mutableMapOf<String, CheckResult>()

        if (dataSource != null) {
            checks["database"] = checkDatabase(dataSource)
        }

        checks["seed"] = CheckResult(
            status = if (seedComplete()) "OK" else "NOT_READY",
            details = mapOf("complete" to seedComplete().toString()),
        )

        extraChecks.forEach { (name, checker) ->
            checks[name] = try {
                checker()
            } catch (e: Exception) {
                logger.error("Health check '{}' failed", name, e)
                CheckResult(status = "ERROR", details = mapOf("error" to (e.message ?: "unknown")))
            }
        }

        val consumers = consumerTrackers.associate { tracker ->
            tracker.topic to ConsumerHealth(
                lastProcessedAt = tracker.lastProcessedAt?.toString(),
                recordsProcessedTotal = tracker.recordsProcessedTotal,
                recordsSentToDlqTotal = tracker.recordsSentToDlqTotal,
                lastErrorAt = tracker.lastErrorAt?.toString(),
                consecutiveErrorCount = tracker.consecutiveErrorCount,
            )
        }

        val allChecksOk = checks.values.all { it.status == "OK" }
        val status = if (allChecksOk) "READY" else "NOT_READY"

        return ReadinessResponse(status = status, checks = checks, consumers = consumers)
    }

    private fun checkDatabase(ds: DataSource): CheckResult {
        return try {
            val details = mutableMapOf<String, String>()

            ds.connection.use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT 1").close()
                }
                details["connection"] = "OK"

                collectPoolStats(ds, details)
                collectFlywayInfo(conn, details)
                checkTimescaleDb(conn, details)
            }

            val pending = details["flywayPendingCount"]?.toIntOrNull() ?: 0
            val poolPending = details["poolPending"]?.toIntOrNull() ?: 0
            val status = if (pending == 0 && poolPending == 0) "OK" else "DEGRADED"

            CheckResult(status = status, details = details)
        } catch (e: Exception) {
            logger.error("Database health check failed", e)
            CheckResult(status = "ERROR", details = mapOf("error" to (e.message ?: "unknown")))
        }
    }

    private fun collectPoolStats(ds: DataSource, details: MutableMap<String, String>) {
        try {
            val hikari = ds as? com.zaxxer.hikari.HikariDataSource ?: return
            val pool = hikari.hikariPoolMXBean ?: return
            details["poolActive"] = pool.activeConnections.toString()
            details["poolIdle"] = pool.idleConnections.toString()
            details["poolPending"] = pool.threadsAwaitingConnection.toString()
            details["poolTotal"] = pool.totalConnections.toString()
        } catch (e: Exception) {
            logger.debug("Could not collect pool stats", e)
        }
    }

    private fun collectFlywayInfo(conn: Connection, details: MutableMap<String, String>) {
        if (flywayLocation == null) return
        try {
            val flyway = org.flywaydb.core.Flyway.configure()
                .dataSource(conn.metaData.url, conn.metaData.userName, null)
                .locations(flywayLocation)
                .load()
            val info = flyway.info()
            val current = info.current()
            details["flywayVersion"] = current?.version?.toString() ?: "none"
            details["flywayPendingCount"] = info.pending().size.toString()
        } catch (e: Exception) {
            logger.debug("Could not collect Flyway info", e)
        }
    }

    private fun checkTimescaleDb(conn: Connection, details: MutableMap<String, String>) {
        try {
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery(
                    "SELECT extname FROM pg_extension WHERE extname = 'timescaledb'",
                )
                details["timescaledbPresent"] = rs.next().toString()
                rs.close()
            }
        } catch (e: Exception) {
            // Not all databases use TimescaleDB — this is expected
            details["timescaledbPresent"] = "false"
        }
    }
}
