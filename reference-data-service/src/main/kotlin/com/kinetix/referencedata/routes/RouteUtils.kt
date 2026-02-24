package com.kinetix.referencedata.routes

import io.ktor.server.application.ApplicationCall

fun ApplicationCall.requirePathParam(name: String): String =
    parameters[name]
        ?: throw IllegalArgumentException("Missing required path parameter: $name")
