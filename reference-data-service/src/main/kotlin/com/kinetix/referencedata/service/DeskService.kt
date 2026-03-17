package com.kinetix.referencedata.service

import com.kinetix.common.model.Desk
import com.kinetix.common.model.DeskId
import com.kinetix.common.model.DivisionId
import com.kinetix.referencedata.persistence.DeskRepository
import com.kinetix.referencedata.persistence.DivisionRepository

class DeskService(
    private val deskRepository: DeskRepository,
    private val divisionRepository: DivisionRepository,
) {
    suspend fun create(desk: Desk) {
        require(divisionRepository.findById(desk.divisionId) != null) {
            "Division '${desk.divisionId.value}' does not exist"
        }
        val desksInDivision = deskRepository.findByDivisionId(desk.divisionId)
        val nameConflict = desksInDivision.any { it.name == desk.name && it.id != desk.id }
        require(!nameConflict) {
            "Desk with name '${desk.name}' already exists in division '${desk.divisionId.value}'"
        }
        deskRepository.save(desk)
    }

    suspend fun findById(id: DeskId): Desk? =
        deskRepository.findById(id)

    suspend fun findAll(): List<Desk> =
        deskRepository.findAll()

    suspend fun findByDivision(divisionId: DivisionId): List<Desk> =
        deskRepository.findByDivisionId(divisionId)
}
