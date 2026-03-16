package com.kinetix.referencedata.persistence

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.InstrumentId
import com.kinetix.referencedata.model.Instrument

interface InstrumentRepository {
    suspend fun save(instrument: Instrument)
    suspend fun findById(instrumentId: InstrumentId): Instrument?
    suspend fun findByType(instrumentType: String): List<Instrument>
    suspend fun findByAssetClass(assetClass: AssetClass): List<Instrument>
    suspend fun findAll(): List<Instrument>
}
