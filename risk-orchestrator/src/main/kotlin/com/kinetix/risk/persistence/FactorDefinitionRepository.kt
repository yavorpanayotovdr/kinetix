package com.kinetix.risk.persistence

import com.kinetix.risk.model.FactorDefinition

interface FactorDefinitionRepository {
    suspend fun findAll(): List<FactorDefinition>
    suspend fun findByName(factorName: String): FactorDefinition?
}
