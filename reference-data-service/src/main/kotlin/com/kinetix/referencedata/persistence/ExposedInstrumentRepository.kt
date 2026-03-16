package com.kinetix.referencedata.persistence

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.instrument.InstrumentType
import com.kinetix.referencedata.model.Instrument
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExposedInstrumentRepository(
    private val db: Database? = null,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : InstrumentRepository {

    override suspend fun save(instrument: Instrument): Unit = newSuspendedTransaction(db = db) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val attributesJson = json.encodeToString(InstrumentType.serializer(), instrument.instrumentType)

        val existing = InstrumentsTable
            .selectAll()
            .where { InstrumentsTable.instrumentId eq instrument.instrumentId.value }
            .singleOrNull()

        if (existing != null) {
            InstrumentsTable.update({ InstrumentsTable.instrumentId eq instrument.instrumentId.value }) {
                it[instrumentType] = instrument.instrumentType.instrumentTypeName
                it[displayName] = instrument.displayName
                it[assetClass] = instrument.assetClass.name
                it[currency] = instrument.currency
                it[attributes] = attributesJson
                it[updatedAt] = now
            }
        } else {
            InstrumentsTable.insert {
                it[instrumentId] = instrument.instrumentId.value
                it[instrumentType] = instrument.instrumentType.instrumentTypeName
                it[displayName] = instrument.displayName
                it[assetClass] = instrument.assetClass.name
                it[currency] = instrument.currency
                it[attributes] = attributesJson
                it[createdAt] = now
                it[updatedAt] = now
            }
        }
    }

    override suspend fun findById(instrumentId: InstrumentId): Instrument? =
        newSuspendedTransaction(db = db) {
            InstrumentsTable
                .selectAll()
                .where { InstrumentsTable.instrumentId eq instrumentId.value }
                .singleOrNull()
                ?.toInstrument()
        }

    override suspend fun findByType(instrumentType: String): List<Instrument> =
        newSuspendedTransaction(db = db) {
            InstrumentsTable
                .selectAll()
                .where { InstrumentsTable.instrumentType eq instrumentType }
                .orderBy(InstrumentsTable.displayName, SortOrder.ASC)
                .map { it.toInstrument() }
        }

    override suspend fun findByAssetClass(assetClass: AssetClass): List<Instrument> =
        newSuspendedTransaction(db = db) {
            InstrumentsTable
                .selectAll()
                .where { InstrumentsTable.assetClass eq assetClass.name }
                .orderBy(InstrumentsTable.displayName, SortOrder.ASC)
                .map { it.toInstrument() }
        }

    override suspend fun findAll(): List<Instrument> =
        newSuspendedTransaction(db = db) {
            InstrumentsTable
                .selectAll()
                .orderBy(InstrumentsTable.displayName, SortOrder.ASC)
                .map { it.toInstrument() }
        }

    private fun ResultRow.toInstrument(): Instrument {
        val attrs = this[InstrumentsTable.attributes]
        val type = json.decodeFromString(InstrumentType.serializer(), attrs)
        return Instrument(
            instrumentId = InstrumentId(this[InstrumentsTable.instrumentId]),
            instrumentType = type,
            displayName = this[InstrumentsTable.displayName],
            currency = this[InstrumentsTable.currency],
            createdAt = this[InstrumentsTable.createdAt].toInstant(),
            updatedAt = this[InstrumentsTable.updatedAt].toInstant(),
        )
    }
}
