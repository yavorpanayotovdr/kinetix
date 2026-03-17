package com.kinetix.referencedata.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class CreateDeskRequest(
    val id: String,
    val name: String,
    val divisionId: String,
    val deskHead: String? = null,
    val description: String? = null,
)
