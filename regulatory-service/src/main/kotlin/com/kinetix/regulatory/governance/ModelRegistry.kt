package com.kinetix.regulatory.governance

import java.time.Instant
import java.util.UUID

class ModelRegistry(private val repository: ModelVersionRepository) {

    private val allowedTransitions = mapOf(
        ModelVersionStatus.DRAFT to setOf(ModelVersionStatus.VALIDATED),
        ModelVersionStatus.VALIDATED to setOf(ModelVersionStatus.APPROVED),
        ModelVersionStatus.APPROVED to setOf(ModelVersionStatus.RETIRED),
        ModelVersionStatus.RETIRED to emptySet(),
    )

    suspend fun register(modelName: String, version: String, parameters: String): ModelVersion {
        val modelVersion = ModelVersion(
            id = UUID.randomUUID().toString(),
            modelName = modelName,
            version = version,
            status = ModelVersionStatus.DRAFT,
            parameters = parameters,
            approvedBy = null,
            approvedAt = null,
            createdAt = Instant.now(),
        )
        repository.save(modelVersion)
        return modelVersion
    }

    suspend fun listAll(): List<ModelVersion> = repository.findAll()

    suspend fun findById(id: String): ModelVersion? = repository.findById(id)

    suspend fun transitionStatus(
        id: String,
        targetStatus: ModelVersionStatus,
        approvedBy: String?,
    ): ModelVersion {
        val model = repository.findById(id)
            ?: throw NoSuchElementException("Model version not found: $id")

        val allowed = allowedTransitions[model.status] ?: emptySet()
        if (targetStatus !in allowed) {
            throw IllegalStateException(
                "Cannot transition from ${model.status} to $targetStatus"
            )
        }

        val updated = model.copy(
            status = targetStatus,
            approvedBy = if (targetStatus == ModelVersionStatus.APPROVED) approvedBy else model.approvedBy,
            approvedAt = if (targetStatus == ModelVersionStatus.APPROVED) Instant.now() else model.approvedAt,
        )
        repository.save(updated)
        return updated
    }
}
