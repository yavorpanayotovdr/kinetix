package com.kinetix.referencedata.model

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.instrument.InstrumentType
import java.time.Instant

data class Instrument(
    val instrumentId: InstrumentId,
    val instrumentType: InstrumentType,
    val displayName: String,
    val currency: String,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val assetClass: AssetClass get() = instrumentType.assetClass()
}
