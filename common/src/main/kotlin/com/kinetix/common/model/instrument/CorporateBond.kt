package com.kinetix.common.model.instrument

import com.kinetix.common.model.AssetClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("CORPORATE_BOND")
data class CorporateBond(
    val currency: String,
    val couponRate: Double,
    val couponFrequency: Int,
    val maturityDate: String,
    val faceValue: Double,
    val issuer: String,
    val creditRating: String? = null,
    val seniority: String? = null,
    val dayCountConvention: String? = null,
) : InstrumentType {
    override val instrumentTypeName: String get() = "CORPORATE_BOND"
    override fun assetClass(): AssetClass = AssetClass.FIXED_INCOME
}
