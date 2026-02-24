package com.kinetix.common.model

data class CurvePoint(val tenor: String, val value: Double) {
    init {
        require(tenor.isNotBlank()) { "Tenor must not be blank" }
    }
}
