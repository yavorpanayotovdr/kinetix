package com.kinetix.referencedata.service

import com.kinetix.common.model.Division
import com.kinetix.common.model.DivisionId
import com.kinetix.referencedata.persistence.DivisionRepository

class DivisionService(
    private val divisionRepository: DivisionRepository,
) {
    suspend fun create(division: Division) {
        val existing = divisionRepository.findByName(division.name)
        require(existing == null || existing.id == division.id) {
            "Division with name '${division.name}' already exists"
        }
        divisionRepository.save(division)
    }

    suspend fun findById(id: DivisionId): Division? =
        divisionRepository.findById(id)

    suspend fun findAll(): List<Division> =
        divisionRepository.findAll()
}
