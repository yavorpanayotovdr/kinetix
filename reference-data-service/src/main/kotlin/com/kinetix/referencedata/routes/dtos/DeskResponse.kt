package com.kinetix.referencedata.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class DeskResponse(
    val id: String,
    val name: String,
    val divisionId: String,
    val deskHead: String? = null,
    val description: String? = null,
    val bookCount: Int,
)
