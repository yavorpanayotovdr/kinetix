package com.kinetix.position.client.dtos

import com.kinetix.common.model.Division
import com.kinetix.common.model.DivisionId
import kotlinx.serialization.Serializable

@Serializable
data class DivisionDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val deskCount: Int = 0,
) {
    fun toDomain() = Division(
        id = DivisionId(id),
        name = name,
        description = description,
    )
}
