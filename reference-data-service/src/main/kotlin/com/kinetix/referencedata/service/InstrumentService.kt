package com.kinetix.referencedata.service

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.InstrumentId
import com.kinetix.referencedata.model.Instrument
import com.kinetix.referencedata.persistence.InstrumentRepository

class InstrumentService(
    private val instrumentRepository: InstrumentRepository,
) {
    suspend fun save(instrument: Instrument) {
        instrumentRepository.save(instrument)
    }

    suspend fun findById(instrumentId: InstrumentId): Instrument? =
        instrumentRepository.findById(instrumentId)

    suspend fun findByType(instrumentType: String): List<Instrument> =
        instrumentRepository.findByType(instrumentType)

    suspend fun findByAssetClass(assetClass: AssetClass): List<Instrument> =
        instrumentRepository.findByAssetClass(assetClass)

    suspend fun findAll(): List<Instrument> =
        instrumentRepository.findAll()
}
