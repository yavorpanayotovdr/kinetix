package com.kinetix.referencedata.persistence

import com.kinetix.common.model.Desk
import com.kinetix.common.model.DeskId
import com.kinetix.common.model.DivisionId

interface DeskRepository {
    suspend fun save(desk: Desk)
    suspend fun findById(id: DeskId): Desk?
    suspend fun findAll(): List<Desk>
    suspend fun findByDivisionId(divisionId: DivisionId): List<Desk>
}
