package com.kinetix.risk.persistence

import com.kinetix.risk.model.AdaptiveVaRParameters
import com.kinetix.risk.model.CalculationType
import com.kinetix.risk.model.ConfidenceLevel
import com.kinetix.risk.model.MarketRegime
import com.kinetix.risk.model.MarketRegimeHistory
import com.kinetix.risk.model.RegimeSignals
import com.kinetix.risk.model.RegimeState
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import java.math.BigDecimal
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class ExposedMarketRegimeRepository(private val db: Database? = null) : MarketRegimeRepository {

    override suspend fun insert(state: RegimeState, id: UUID): UUID =
        newSuspendedTransaction(db = db) {
            MarketRegimeHistoryTable.insert { row ->
                row[MarketRegimeHistoryTable.id] = id
                row[regime] = state.regime.name
                row[startedAt] = OffsetDateTime.ofInstant(state.detectedAt, ZoneOffset.UTC)
                row[endedAt] = null
                row[durationMs] = null
                row[realisedVol20d] = BigDecimal.valueOf(state.signals.realisedVol20d)
                row[crossAssetCorrelation] = BigDecimal.valueOf(state.signals.crossAssetCorrelation)
                row[creditSpreadBps] = state.signals.creditSpreadBps?.let { BigDecimal.valueOf(it) }
                row[pnlVolatility] = state.signals.pnlVolatility?.let { BigDecimal.valueOf(it) }
                row[calculationType] = state.varParameters.calculationType.name
                row[confidenceLevel] = state.varParameters.confidenceLevel.name
                row[timeHorizonDays] = state.varParameters.timeHorizonDays
                row[correlationMethod] = state.varParameters.correlationMethod
                row[numSimulations] = state.varParameters.numSimulations
                row[confidence] = BigDecimal.valueOf(state.confidence)
                row[degradedInputs] = state.degradedInputs
                row[consecutiveObservations] = state.consecutiveObservations
                row[createdAt] = OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC)
            }
            id
        }

    override suspend fun close(id: UUID, endedAt: Instant): Unit =
        newSuspendedTransaction(db = db) {
            MarketRegimeHistoryTable.update(
                where = { MarketRegimeHistoryTable.id eq id }
            ) { row ->
                val endedAtOdt = OffsetDateTime.ofInstant(endedAt, ZoneOffset.UTC)
                row[MarketRegimeHistoryTable.endedAt] = endedAtOdt
                // We cannot easily compute durationMs without a join; the caller may pass it via a full update
            }
        }

    override suspend fun findCurrent(): MarketRegimeHistory? =
        newSuspendedTransaction(db = db) {
            MarketRegimeHistoryTable
                .selectAll()
                .where { MarketRegimeHistoryTable.endedAt.isNull() }
                .orderBy(MarketRegimeHistoryTable.startedAt, SortOrder.DESC)
                .limit(1)
                .singleOrNull()
                ?.toHistory()
        }

    override suspend fun findRecent(limit: Int): List<MarketRegimeHistory> =
        newSuspendedTransaction(db = db) {
            MarketRegimeHistoryTable
                .selectAll()
                .orderBy(MarketRegimeHistoryTable.startedAt, SortOrder.DESC)
                .limit(limit)
                .map { it.toHistory() }
        }

    private fun org.jetbrains.exposed.sql.ResultRow.toHistory(): MarketRegimeHistory {
        val row = this
        return MarketRegimeHistory(
            id = row[MarketRegimeHistoryTable.id],
            regime = MarketRegime.valueOf(row[MarketRegimeHistoryTable.regime]),
            startedAt = row[MarketRegimeHistoryTable.startedAt].toInstant(),
            endedAt = row[MarketRegimeHistoryTable.endedAt]?.toInstant(),
            durationMs = row[MarketRegimeHistoryTable.durationMs],
            signals = RegimeSignals(
                realisedVol20d = row[MarketRegimeHistoryTable.realisedVol20d].toDouble(),
                crossAssetCorrelation = row[MarketRegimeHistoryTable.crossAssetCorrelation].toDouble(),
                creditSpreadBps = row[MarketRegimeHistoryTable.creditSpreadBps]?.toDouble(),
                pnlVolatility = row[MarketRegimeHistoryTable.pnlVolatility]?.toDouble(),
            ),
            varParameters = AdaptiveVaRParameters(
                calculationType = CalculationType.valueOf(row[MarketRegimeHistoryTable.calculationType]),
                confidenceLevel = ConfidenceLevel.valueOf(row[MarketRegimeHistoryTable.confidenceLevel]),
                timeHorizonDays = row[MarketRegimeHistoryTable.timeHorizonDays],
                correlationMethod = row[MarketRegimeHistoryTable.correlationMethod],
                numSimulations = row[MarketRegimeHistoryTable.numSimulations],
            ),
            confidence = row[MarketRegimeHistoryTable.confidence].toDouble(),
            degradedInputs = row[MarketRegimeHistoryTable.degradedInputs],
            consecutiveObservations = row[MarketRegimeHistoryTable.consecutiveObservations],
        )
    }
}
