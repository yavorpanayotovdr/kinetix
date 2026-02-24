package com.kinetix.correlation.routes

import io.ktor.server.application.ApplicationCall

fun ApplicationCall.requireQueryParam(name: String): String =
    request.queryParameters[name]
        ?: throw IllegalArgumentException("Missing required query parameter: $name")
