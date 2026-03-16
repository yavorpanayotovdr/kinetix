package com.kinetix.referencedata.routes

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.instrument.InstrumentType
import com.kinetix.referencedata.model.Instrument
import com.kinetix.referencedata.routes.dtos.CreateInstrumentRequest
import com.kinetix.referencedata.routes.dtos.InstrumentResponse
import com.kinetix.referencedata.service.InstrumentService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.time.Instant

private val json = Json { ignoreUnknownKeys = true }

fun Route.instrumentRoutes(instrumentService: InstrumentService) {
    route("/api/v1/instruments") {
        get({
            summary = "List instruments"
            tags = listOf("Instruments")
            request {
                queryParameter<String>("type") { description = "Filter by instrument type"; required = false }
                queryParameter<String>("assetClass") { description = "Filter by asset class"; required = false }
            }
        }) {
            val type = call.request.queryParameters["type"]
            val assetClass = call.request.queryParameters["assetClass"]

            val instruments = when {
                type != null -> instrumentService.findByType(type)
                assetClass != null -> instrumentService.findByAssetClass(AssetClass.valueOf(assetClass))
                else -> instrumentService.findAll()
            }
            call.respond(instruments.map { it.toResponse() })
        }

        post({
            summary = "Create an instrument"
            tags = listOf("Instruments")
            request {
                body<CreateInstrumentRequest>()
            }
        }) {
            val request = call.receive<CreateInstrumentRequest>()
            val instrumentType = json.decodeFromString(
                InstrumentType.serializer(),
                request.attributes.toString(),
            )

            require(instrumentType.instrumentTypeName == request.instrumentType) {
                "Attributes type discriminator must match instrumentType field"
            }

            val instrument = Instrument(
                instrumentId = InstrumentId(request.instrumentId),
                instrumentType = instrumentType,
                displayName = request.displayName,
                currency = request.currency,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
            instrumentService.save(instrument)
            call.respond(HttpStatusCode.Created, instrument.toResponse())
        }

        route("/{id}") {
            get({
                summary = "Get instrument by ID"
                tags = listOf("Instruments")
                request {
                    pathParameter<String>("id") { description = "Instrument identifier" }
                }
            }) {
                val id = InstrumentId(call.requirePathParam("id"))
                val instrument = instrumentService.findById(id)
                if (instrument != null) {
                    call.respond(instrument.toResponse())
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }
    }
}

private fun Instrument.toResponse(): InstrumentResponse {
    val attrsJson = json.encodeToString(InstrumentType.serializer(), instrumentType)
    return InstrumentResponse(
        instrumentId = instrumentId.value,
        instrumentType = instrumentType.instrumentTypeName,
        displayName = displayName,
        assetClass = assetClass.name,
        currency = currency,
        attributes = json.decodeFromString(JsonObject.serializer(), attrsJson),
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString(),
    )
}
