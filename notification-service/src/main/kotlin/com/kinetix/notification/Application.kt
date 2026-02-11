package com.kinetix.notification

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    install(ContentNegotiation) { json() }
    routing {
        get("/health") {
            call.respondText("""{"status":"UP"}""", ContentType.Application.Json)
        }
    }
}
