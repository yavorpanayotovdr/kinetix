package com.kinetix.correlation.kafka

import com.kinetix.common.model.CorrelationMatrix
import kotlinx.serialization.Serializable

@Serializable
data class CorrelationMatrixEvent(
    val labels: List<String>,
    val windowDays: Int,
    val matrixSize: Int,
    val asOfDate: String,
    val method: String,
) {
    companion object {
        fun from(m: CorrelationMatrix) = CorrelationMatrixEvent(
            labels = m.labels,
            windowDays = m.windowDays,
            matrixSize = m.labels.size,
            asOfDate = m.asOfDate.toString(),
            method = m.method.name,
        )
    }
}
