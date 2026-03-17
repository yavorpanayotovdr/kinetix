package com.kinetix.referencedata.persistence

import com.kinetix.common.model.Division
import com.kinetix.common.model.DivisionId

interface DivisionRepository {
    suspend fun save(division: Division)
    suspend fun findById(id: DivisionId): Division?
    suspend fun findByName(name: String): Division?
    suspend fun findAll(): List<Division>
}
