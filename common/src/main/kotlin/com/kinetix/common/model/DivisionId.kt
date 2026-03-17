package com.kinetix.common.model

data class DivisionId(val value: String) {
    init {
        require(value.isNotBlank()) { "DivisionId must not be blank" }
    }
}
