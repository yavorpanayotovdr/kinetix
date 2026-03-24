package com.kinetix.risk.persistence

import com.kinetix.common.model.BookId
import com.kinetix.risk.model.InstrumentPnlBreakdown
import com.kinetix.risk.model.IntradayPnlSnapshot
import com.kinetix.risk.model.PnlTrigger
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Serializable
private data class InstrumentPnlRecord(
    val instrumentId: String,
    val assetClass: String,
    val totalPnl: String,
    val deltaPnl: String,
    val gammaPnl: String,
    val vegaPnl: String,
    val thetaPnl: String,
    val rhoPnl: String,
    val unexplainedPnl: String,
)

private val pnlJson = Json { ignoreUnknownKeys = true }

private fun List<InstrumentPnlBreakdown>.toJson(): String =
    pnlJson.encodeToString(map {
        InstrumentPnlRecord(
            instrumentId = it.instrumentId,
            assetClass = it.assetClass,
            totalPnl = it.totalPnl,
            deltaPnl = it.deltaPnl,
            gammaPnl = it.gammaPnl,
            vegaPnl = it.vegaPnl,
            thetaPnl = it.thetaPnl,
            rhoPnl = it.rhoPnl,
            unexplainedPnl = it.unexplainedPnl,
        )
    })

private fun String.toInstrumentPnlBreakdowns(): List<InstrumentPnlBreakdown> =
    pnlJson.decodeFromString<List<InstrumentPnlRecord>>(this).map {
        InstrumentPnlBreakdown(
            instrumentId = it.instrumentId,
            assetClass = it.assetClass,
            totalPnl = it.totalPnl,
            deltaPnl = it.deltaPnl,
            gammaPnl = it.gammaPnl,
            vegaPnl = it.vegaPnl,
            thetaPnl = it.thetaPnl,
            rhoPnl = it.rhoPnl,
            unexplainedPnl = it.unexplainedPnl,
        )
    }

class ExposedIntradayPnlRepository(private val db: Database? = null) : IntradayPnlRepository {

    override suspend fun save(snapshot: IntradayPnlSnapshot): Unit = newSuspendedTransaction(db = db) {
        IntradayPnlSnapshotsTable.insert {
            it[bookId] = snapshot.bookId.value
            it[snapshotAt] = OffsetDateTime.ofInstant(snapshot.snapshotAt, ZoneOffset.UTC)
            it[baseCurrency] = snapshot.baseCurrency
            it[trigger] = snapshot.trigger.name
            it[totalPnl] = snapshot.totalPnl
            it[realisedPnl] = snapshot.realisedPnl
            it[unrealisedPnl] = snapshot.unrealisedPnl
            it[deltaPnl] = snapshot.deltaPnl
            it[gammaPnl] = snapshot.gammaPnl
            it[vegaPnl] = snapshot.vegaPnl
            it[thetaPnl] = snapshot.thetaPnl
            it[rhoPnl] = snapshot.rhoPnl
            it[unexplainedPnl] = snapshot.unexplainedPnl
            it[highWaterMark] = snapshot.highWaterMark
            it[instrumentPnlJson] = snapshot.instrumentPnl.toJson()
            it[correlationId] = snapshot.correlationId
        }
    }

    override suspend fun findLatest(bookId: BookId): IntradayPnlSnapshot? = newSuspendedTransaction(db = db) {
        IntradayPnlSnapshotsTable
            .selectAll()
            .where { IntradayPnlSnapshotsTable.bookId eq bookId.value }
            .orderBy(IntradayPnlSnapshotsTable.snapshotAt, SortOrder.DESC)
            .limit(1)
            .firstOrNull()
            ?.toSnapshot()
    }

    override suspend fun findSeries(
        bookId: BookId,
        from: Instant,
        to: Instant,
    ): List<IntradayPnlSnapshot> = newSuspendedTransaction(db = db) {
        val fromOffset = OffsetDateTime.ofInstant(from, ZoneOffset.UTC)
        val toOffset = OffsetDateTime.ofInstant(to, ZoneOffset.UTC)
        IntradayPnlSnapshotsTable
            .selectAll()
            .where {
                (IntradayPnlSnapshotsTable.bookId eq bookId.value) and
                    (IntradayPnlSnapshotsTable.snapshotAt greaterEq fromOffset) and
                    (IntradayPnlSnapshotsTable.snapshotAt lessEq toOffset)
            }
            .orderBy(IntradayPnlSnapshotsTable.snapshotAt, SortOrder.ASC)
            .map { it.toSnapshot() }
    }

    private fun ResultRow.toSnapshot(): IntradayPnlSnapshot = IntradayPnlSnapshot(
        id = this[IntradayPnlSnapshotsTable.id],
        bookId = BookId(this[IntradayPnlSnapshotsTable.bookId]),
        snapshotAt = this[IntradayPnlSnapshotsTable.snapshotAt].toInstant(),
        baseCurrency = this[IntradayPnlSnapshotsTable.baseCurrency],
        trigger = PnlTrigger.valueOf(this[IntradayPnlSnapshotsTable.trigger]),
        totalPnl = this[IntradayPnlSnapshotsTable.totalPnl],
        realisedPnl = this[IntradayPnlSnapshotsTable.realisedPnl],
        unrealisedPnl = this[IntradayPnlSnapshotsTable.unrealisedPnl],
        deltaPnl = this[IntradayPnlSnapshotsTable.deltaPnl],
        gammaPnl = this[IntradayPnlSnapshotsTable.gammaPnl],
        vegaPnl = this[IntradayPnlSnapshotsTable.vegaPnl],
        thetaPnl = this[IntradayPnlSnapshotsTable.thetaPnl],
        rhoPnl = this[IntradayPnlSnapshotsTable.rhoPnl],
        unexplainedPnl = this[IntradayPnlSnapshotsTable.unexplainedPnl],
        highWaterMark = this[IntradayPnlSnapshotsTable.highWaterMark],
        instrumentPnl = this[IntradayPnlSnapshotsTable.instrumentPnlJson].toInstrumentPnlBreakdowns(),
        correlationId = this[IntradayPnlSnapshotsTable.correlationId],
    )
}
