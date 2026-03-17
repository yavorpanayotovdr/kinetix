package com.kinetix.referencedata.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class DivisionResponse(
    val id: String,
    val name: String,
    val description: String? = null,
    val deskCount: Int,
)
