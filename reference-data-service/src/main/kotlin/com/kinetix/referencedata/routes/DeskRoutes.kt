package com.kinetix.referencedata.routes

import com.kinetix.common.model.Desk
import com.kinetix.common.model.DeskId
import com.kinetix.common.model.DivisionId
import com.kinetix.referencedata.routes.dtos.CreateDeskRequest
import com.kinetix.referencedata.routes.dtos.DeskResponse
import com.kinetix.referencedata.service.DeskService
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

fun Route.deskRoutes(deskService: DeskService) {
    route("/api/v1/desks") {
        get({
            summary = "List all desks"
            tags = listOf("Desks")
        }) {
            val desks = deskService.findAll()
            call.respond(desks.map { it.toResponse(bookCount = 0) })
        }

        post({
            summary = "Create a desk"
            tags = listOf("Desks")
            request { body<CreateDeskRequest>() }
        }) {
            val request = call.receive<CreateDeskRequest>()
            val desk = Desk(
                id = DeskId(request.id),
                name = request.name,
                divisionId = DivisionId(request.divisionId),
                deskHead = request.deskHead,
                description = request.description,
            )
            deskService.create(desk)
            call.respond(HttpStatusCode.Created, desk.toResponse(bookCount = 0))
        }

        route("/{id}") {
            get({
                summary = "Get desk by ID"
                tags = listOf("Desks")
                request { pathParameter<String>("id") { description = "Desk identifier" } }
            }) {
                val id = DeskId(call.requirePathParam("id"))
                val desk = deskService.findById(id)
                if (desk != null) {
                    call.respond(desk.toResponse(bookCount = 0))
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }
    }
}

private fun Desk.toResponse(bookCount: Int) = DeskResponse(
    id = id.value,
    name = name,
    divisionId = divisionId.value,
    deskHead = deskHead,
    description = description,
    bookCount = bookCount,
)
