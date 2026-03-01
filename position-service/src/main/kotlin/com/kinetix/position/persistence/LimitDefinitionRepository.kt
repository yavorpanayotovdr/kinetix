package com.kinetix.position.persistence

import com.kinetix.position.model.LimitDefinition
import com.kinetix.position.model.LimitLevel
import com.kinetix.position.model.LimitType

interface LimitDefinitionRepository {
    suspend fun findByEntityAndType(entityId: String, level: LimitLevel, limitType: LimitType): LimitDefinition?
    suspend fun findAll(): List<LimitDefinition>
    suspend fun save(limitDefinition: LimitDefinition)
    suspend fun update(limitDefinition: LimitDefinition)
    suspend fun findById(id: String): LimitDefinition?
}
