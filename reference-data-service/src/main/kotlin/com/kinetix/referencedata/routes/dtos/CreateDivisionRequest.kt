package com.kinetix.referencedata.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class CreateDivisionRequest(
    val id: String,
    val name: String,
    val description: String? = null,
)
