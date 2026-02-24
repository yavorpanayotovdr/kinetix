package com.kinetix.risk.client.dtos

import com.kinetix.common.model.CorrelationMatrix
import com.kinetix.common.model.EstimationMethod
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class CorrelationMatrixDto(
    val labels: List<String>,
    val values: List<Double>,
    val windowDays: Int,
    val asOfDate: String,
    val method: String,
) {
    fun toDomain() = CorrelationMatrix(
        labels = labels,
        values = values,
        windowDays = windowDays,
        asOfDate = Instant.parse(asOfDate),
        method = EstimationMethod.valueOf(method),
    )
}
