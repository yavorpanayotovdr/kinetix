package com.kinetix.position.client.dtos

import com.kinetix.common.model.Desk
import com.kinetix.common.model.DeskId
import com.kinetix.common.model.DivisionId
import kotlinx.serialization.Serializable

@Serializable
data class DeskDto(
    val id: String,
    val name: String,
    val divisionId: String,
    val deskHead: String? = null,
    val description: String? = null,
    val bookCount: Int = 0,
) {
    fun toDomain() = Desk(
        id = DeskId(id),
        name = name,
        divisionId = DivisionId(divisionId),
        deskHead = deskHead,
        description = description,
    )
}
