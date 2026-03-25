package com.kinetix.regulatory.governance

import com.kinetix.common.audit.AuditEventType
import com.kinetix.common.audit.GovernanceAuditEvent
import com.kinetix.regulatory.audit.GovernanceAuditPublisher
import java.time.Instant
import java.util.UUID

class ModelRegistry(
    private val repository: ModelVersionRepository,
    private val auditPublisher: GovernanceAuditPublisher? = null,
) {

    private val allowedTransitions = mapOf(
        ModelVersionStatus.DRAFT to setOf(ModelVersionStatus.VALIDATED),
        ModelVersionStatus.VALIDATED to setOf(ModelVersionStatus.APPROVED),
        ModelVersionStatus.APPROVED to setOf(ModelVersionStatus.RETIRED),
        ModelVersionStatus.RETIRED to emptySet(),
    )

    suspend fun register(modelName: String, version: String, parameters: String, registeredBy: String): ModelVersion {
        val modelVersion = ModelVersion(
            id = UUID.randomUUID().toString(),
            modelName = modelName,
            version = version,
            status = ModelVersionStatus.DRAFT,
            parameters = parameters,
            registeredBy = registeredBy,
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

        if (targetStatus == ModelVersionStatus.APPROVED && approvedBy == model.registeredBy) {
            throw IllegalArgumentException(
                "Self-approval is not permitted: approvedBy and registeredBy cannot be the same user"
            )
        }

        val updated = model.copy(
            status = targetStatus,
            approvedBy = if (targetStatus == ModelVersionStatus.APPROVED) approvedBy else model.approvedBy,
            approvedAt = if (targetStatus == ModelVersionStatus.APPROVED) Instant.now() else model.approvedAt,
        )
        repository.save(updated)

        auditPublisher?.publish(
            GovernanceAuditEvent(
                eventType = AuditEventType.MODEL_STATUS_CHANGED,
                userId = approvedBy ?: "SYSTEM",
                userRole = if (approvedBy != null) "APPROVER" else "SYSTEM",
                modelName = model.modelName,
                details = "${model.status}->${targetStatus}",
            )
        )

        return updated
    }
}
