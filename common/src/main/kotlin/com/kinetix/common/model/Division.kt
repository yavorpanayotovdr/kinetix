package com.kinetix.common.model

data class Division(
    val id: DivisionId,
    val name: String,
    val description: String? = null,
) {
    init {
        require(name.isNotBlank()) { "Division name must not be blank" }
    }
}
