package com.kinetix.regulatory.governance

interface ModelVersionRepository {
    suspend fun save(modelVersion: ModelVersion)
    suspend fun findById(id: String): ModelVersion?
    suspend fun findAll(): List<ModelVersion>
}
