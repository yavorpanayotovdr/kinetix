package com.kinetix.volatility.persistence

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.VolPoint
import com.kinetix.common.model.VolSurface
import com.kinetix.common.model.VolatilitySource
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExposedVolSurfaceRepository(private val db: Database? = null) : VolSurfaceRepository {

    override suspend fun save(surface: VolSurface): Unit = newSuspendedTransaction(db = db) {
        val asOfOffset = surface.asOf.atOffset(ZoneOffset.UTC)
        VolSurfaceTable.insert {
            it[instrumentId] = surface.instrumentId.value
            it[asOfDate] = asOfOffset
            it[dataSource] = surface.source.name
            it[createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
        for (point in surface.points) {
            VolPointTable.insert {
                it[instrumentId] = surface.instrumentId.value
                it[asOfDate] = asOfOffset
                it[strike] = point.strike
                it[maturityDays] = point.maturityDays
                it[impliedVol] = point.impliedVol
            }
        }
    }

    override suspend fun findLatest(instrumentId: InstrumentId): VolSurface? =
        newSuspendedTransaction(db = db) {
            val latestHeader = VolSurfaceTable
                .selectAll()
                .where { VolSurfaceTable.instrumentId eq instrumentId.value }
                .orderBy(VolSurfaceTable.asOfDate, SortOrder.DESC)
                .limit(1)
                .singleOrNull() ?: return@newSuspendedTransaction null

            val asOfDate = latestHeader[VolSurfaceTable.asOfDate]
            val pointRows = VolPointTable
                .selectAll()
                .where {
                    (VolPointTable.instrumentId eq instrumentId.value) and
                        (VolPointTable.asOfDate eq asOfDate)
                }
                .toList()

            toVolSurface(latestHeader, pointRows)
        }

    override suspend fun findByTimeRange(
        instrumentId: InstrumentId,
        from: Instant,
        to: Instant,
    ): List<VolSurface> = newSuspendedTransaction(db = db) {
        val fromOffset = from.atOffset(ZoneOffset.UTC)
        val toOffset = to.atOffset(ZoneOffset.UTC)

        val headers = VolSurfaceTable
            .selectAll()
            .where {
                (VolSurfaceTable.instrumentId eq instrumentId.value) and
                    (VolSurfaceTable.asOfDate greaterEq fromOffset) and
                    (VolSurfaceTable.asOfDate lessEq toOffset)
            }
            .orderBy(VolSurfaceTable.asOfDate, SortOrder.ASC)
            .toList()

        headers.map { header ->
            val pointRows = VolPointTable
                .selectAll()
                .where {
                    (VolPointTable.instrumentId eq header[VolSurfaceTable.instrumentId]) and
                        (VolPointTable.asOfDate eq header[VolSurfaceTable.asOfDate])
                }
                .toList()
            toVolSurface(header, pointRows)
        }
    }

    private fun toVolSurface(header: ResultRow, pointRows: List<ResultRow>): VolSurface {
        val points = pointRows.map { row ->
            VolPoint(
                strike = row[VolPointTable.strike],
                maturityDays = row[VolPointTable.maturityDays],
                impliedVol = row[VolPointTable.impliedVol],
            )
        }
        return VolSurface(
            instrumentId = InstrumentId(header[VolSurfaceTable.instrumentId]),
            asOf = header[VolSurfaceTable.asOfDate].toInstant(),
            points = points,
            source = VolatilitySource.valueOf(header[VolSurfaceTable.dataSource]),
        )
    }
}
