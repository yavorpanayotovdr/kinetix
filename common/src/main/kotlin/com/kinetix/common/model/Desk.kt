package com.kinetix.common.model

data class Desk(
    val id: DeskId,
    val name: String,
    val divisionId: DivisionId,
    val deskHead: String? = null,
    val description: String? = null,
) {
    init {
        require(name.isNotBlank()) { "Desk name must not be blank" }
    }
}
