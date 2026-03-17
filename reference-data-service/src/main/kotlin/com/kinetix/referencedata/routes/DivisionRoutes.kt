package com.kinetix.referencedata.routes

import com.kinetix.common.model.Division
import com.kinetix.common.model.DivisionId
import com.kinetix.referencedata.routes.dtos.CreateDivisionRequest
import com.kinetix.referencedata.routes.dtos.DeskResponse
import com.kinetix.referencedata.routes.dtos.DivisionResponse
import com.kinetix.referencedata.service.DeskService
import com.kinetix.referencedata.service.DivisionService
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

fun Route.divisionRoutes(divisionService: DivisionService, deskService: DeskService) {
    route("/api/v1/divisions") {
        get({
            summary = "List all divisions"
            tags = listOf("Divisions")
        }) {
            val divisions = divisionService.findAll()
            val responses = divisions.map { division ->
                val deskCount = deskService.findByDivision(division.id).size
                division.toResponse(deskCount)
            }
            call.respond(responses)
        }

        post({
            summary = "Create a division"
            tags = listOf("Divisions")
            request { body<CreateDivisionRequest>() }
        }) {
            val request = call.receive<CreateDivisionRequest>()
            val division = Division(
                id = DivisionId(request.id),
                name = request.name,
                description = request.description,
            )
            divisionService.create(division)
            call.respond(HttpStatusCode.Created, division.toResponse(deskCount = 0))
        }

        route("/{id}") {
            get({
                summary = "Get division by ID"
                tags = listOf("Divisions")
                request { pathParameter<String>("id") { description = "Division identifier" } }
            }) {
                val id = DivisionId(call.requirePathParam("id"))
                val division = divisionService.findById(id)
                if (division != null) {
                    val deskCount = deskService.findByDivision(id).size
                    call.respond(division.toResponse(deskCount))
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            route("/desks") {
                get({
                    summary = "List desks in division"
                    tags = listOf("Divisions")
                    request { pathParameter<String>("id") { description = "Division identifier" } }
                }) {
                    val divisionId = DivisionId(call.requirePathParam("id"))
                    val desks = deskService.findByDivision(divisionId)
                    call.respond(desks.map { it.toResponse(bookCount = 0) })
                }
            }
        }
    }
}

private fun Division.toResponse(deskCount: Int) = DivisionResponse(
    id = id.value,
    name = name,
    description = description,
    deskCount = deskCount,
)

private fun com.kinetix.common.model.Desk.toResponse(bookCount: Int) = DeskResponse(
    id = id.value,
    name = name,
    divisionId = divisionId.value,
    deskHead = deskHead,
    description = description,
    bookCount = bookCount,
)
