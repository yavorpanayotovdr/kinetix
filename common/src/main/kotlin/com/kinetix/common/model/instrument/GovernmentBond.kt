package com.kinetix.common.model.instrument

import com.kinetix.common.model.AssetClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("GOVERNMENT_BOND")
data class GovernmentBond(
    val currency: String,
    val couponRate: Double,
    val couponFrequency: Int,
    val maturityDate: String,
    val faceValue: Double,
    val dayCountConvention: String? = null,
) : InstrumentType {
    override val instrumentTypeName: String get() = "GOVERNMENT_BOND"
    override fun assetClass(): AssetClass = AssetClass.FIXED_INCOME
}
