package com.kinetix.common.model

data class InstrumentId(val value: String) {
    init {
        require(value.isNotBlank()) { "InstrumentId must not be blank" }
    }
}
