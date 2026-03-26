package com.kinetix.risk.routes

import com.kinetix.risk.model.FactorDefinition
import com.kinetix.risk.persistence.FactorDefinitionRepository
import com.kinetix.risk.routes.dtos.FactorDefinitionResponse
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.factorDefinitionRoutes(repository: FactorDefinitionRepository) {

    get("/api/v1/factor-definitions") {
        call.respond(repository.findAll().map { it.toResponse() })
    }

    get("/api/v1/factor-definitions/{name}") {
        val name = call.requirePathParam("name")
        val definition = repository.findByName(name)

        if (definition == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respond(definition.toResponse())
        }
    }
}

private fun FactorDefinition.toResponse() = FactorDefinitionResponse(
    factorName = factorName,
    proxyInstrumentId = proxyInstrumentId,
    description = description,
)
