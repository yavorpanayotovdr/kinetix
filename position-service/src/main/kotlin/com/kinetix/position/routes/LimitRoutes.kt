package com.kinetix.position.routes

import com.kinetix.position.model.LimitDefinition
import com.kinetix.position.model.LimitLevel
import com.kinetix.position.model.LimitType
import com.kinetix.position.model.TemporaryLimitIncrease
import com.kinetix.position.persistence.LimitDefinitionRepository
import com.kinetix.position.persistence.TemporaryLimitIncreaseRepository
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Serializable
data class LimitDefinitionResponse(
    val id: String,
    val level: String,
    val entityId: String,
    val limitType: String,
    val limitValue: String,
    val intradayLimit: String?,
    val overnightLimit: String?,
    val active: Boolean,
)

@Serializable
data class CreateLimitRequest(
    val level: String,
    val entityId: String,
    val limitType: String,
    val limitValue: String,
    val intradayLimit: String? = null,
    val overnightLimit: String? = null,
)

@Serializable
data class UpdateLimitRequest(
    val limitValue: String,
    val intradayLimit: String? = null,
    val overnightLimit: String? = null,
    val active: Boolean? = null,
)

@Serializable
data class TemporaryIncreaseRequest(
    val newValue: String,
    val approvedBy: String,
    val expiresAt: String,
    val reason: String,
)

@Serializable
data class TemporaryIncreaseResponse(
    val id: String,
    val limitId: String,
    val newValue: String,
    val approvedBy: String,
    val expiresAt: String,
    val reason: String,
    val createdAt: String,
)

private fun LimitDefinition.toResponse() = LimitDefinitionResponse(
    id = id,
    level = level.name,
    entityId = entityId,
    limitType = limitType.name,
    limitValue = limitValue.toPlainString(),
    intradayLimit = intradayLimit?.toPlainString(),
    overnightLimit = overnightLimit?.toPlainString(),
    active = active,
)

private fun TemporaryLimitIncrease.toResponse() = TemporaryIncreaseResponse(
    id = id,
    limitId = limitId,
    newValue = newValue.toPlainString(),
    approvedBy = approvedBy,
    expiresAt = expiresAt.toString(),
    reason = reason,
    createdAt = createdAt.toString(),
)

fun Route.limitRoutes(
    limitDefinitionRepo: LimitDefinitionRepository,
    temporaryLimitIncreaseRepo: TemporaryLimitIncreaseRepository,
) {
    route("/api/v1/limits") {

        get({
            summary = "List all limit definitions"
            tags = listOf("Limits")
            response {
                code(HttpStatusCode.OK) { body<List<LimitDefinitionResponse>>() }
            }
        }) {
            val limits = limitDefinitionRepo.findAll()
            call.respond(limits.map { it.toResponse() })
        }

        post({
            summary = "Create a limit definition"
            tags = listOf("Limits")
            request {
                body<CreateLimitRequest>()
            }
            response {
                code(HttpStatusCode.Created) { body<LimitDefinitionResponse>() }
                code(HttpStatusCode.BadRequest) { body<ErrorResponse>() }
            }
        }) {
            val request = call.receive<CreateLimitRequest>()
            val limitDefinition = LimitDefinition(
                id = UUID.randomUUID().toString(),
                level = LimitLevel.valueOf(request.level),
                entityId = request.entityId,
                limitType = LimitType.valueOf(request.limitType),
                limitValue = BigDecimal(request.limitValue),
                intradayLimit = request.intradayLimit?.let { BigDecimal(it) },
                overnightLimit = request.overnightLimit?.let { BigDecimal(it) },
                active = true,
            )
            limitDefinitionRepo.save(limitDefinition)
            call.respond(HttpStatusCode.Created, limitDefinition.toResponse())
        }

        route("/{id}") {

            put({
                summary = "Update a limit definition"
                tags = listOf("Limits")
                request {
                    pathParameter<String>("id") { description = "Limit definition ID" }
                    body<UpdateLimitRequest>()
                }
                response {
                    code(HttpStatusCode.OK) { body<LimitDefinitionResponse>() }
                    code(HttpStatusCode.NotFound) { body<ErrorResponse>() }
                }
            }) {
                val id = call.requirePathParam("id")
                val existing = limitDefinitionRepo.findById(id)
                    ?: return@put call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse("not_found", "Limit definition not found"),
                    )

                val request = call.receive<UpdateLimitRequest>()
                val updated = existing.copy(
                    limitValue = BigDecimal(request.limitValue),
                    intradayLimit = request.intradayLimit?.let { BigDecimal(it) } ?: existing.intradayLimit,
                    overnightLimit = request.overnightLimit?.let { BigDecimal(it) } ?: existing.overnightLimit,
                    active = request.active ?: existing.active,
                )
                limitDefinitionRepo.update(updated)
                call.respond(updated.toResponse())
            }

            post("/temporary-increase", {
                summary = "Request a temporary limit increase"
                tags = listOf("Limits")
                request {
                    pathParameter<String>("id") { description = "Limit definition ID" }
                    body<TemporaryIncreaseRequest>()
                }
                response {
                    code(HttpStatusCode.Created) { body<TemporaryIncreaseResponse>() }
                    code(HttpStatusCode.NotFound) { body<ErrorResponse>() }
                }
            }) {
                val limitId = call.requirePathParam("id")
                val existing = limitDefinitionRepo.findById(limitId)
                    ?: return@post call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse("not_found", "Limit definition not found"),
                    )

                val request = call.receive<TemporaryIncreaseRequest>()
                val tempIncrease = TemporaryLimitIncrease(
                    id = UUID.randomUUID().toString(),
                    limitId = limitId,
                    newValue = BigDecimal(request.newValue),
                    approvedBy = request.approvedBy,
                    expiresAt = Instant.parse(request.expiresAt),
                    reason = request.reason,
                    createdAt = Instant.now(),
                )
                temporaryLimitIncreaseRepo.save(tempIncrease)
                call.respond(HttpStatusCode.Created, tempIncrease.toResponse())
            }
        }
    }
}
