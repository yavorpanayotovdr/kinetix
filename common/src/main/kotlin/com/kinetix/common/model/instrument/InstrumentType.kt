package com.kinetix.common.model.instrument

import com.kinetix.common.model.AssetClass
import kotlinx.serialization.Serializable

@Serializable
sealed interface InstrumentType {
    val instrumentTypeName: String
    fun assetClass(): AssetClass
}
