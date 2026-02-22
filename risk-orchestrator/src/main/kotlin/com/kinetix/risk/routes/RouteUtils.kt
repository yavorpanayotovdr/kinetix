package com.kinetix.risk.routes

import io.ktor.server.application.*

fun ApplicationCall.requirePathParam(name: String): String =
    parameters[name]
        ?: throw IllegalArgumentException("Missing required path parameter: $name")
