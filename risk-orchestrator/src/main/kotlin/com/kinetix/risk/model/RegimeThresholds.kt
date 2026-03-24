package com.kinetix.risk.model

data class RegimeThresholds(
    val normalVolCeiling: Double,
    val elevatedVolCeiling: Double,
    val crisisCorrelationFloor: Double,
) {
    companion object {
        /** Default thresholds calibrated from 10-year historical lookback. */
        val DEFAULT = RegimeThresholds(
            normalVolCeiling = 0.15,
            elevatedVolCeiling = 0.25,
            crisisCorrelationFloor = 0.75,
        )
    }
}
