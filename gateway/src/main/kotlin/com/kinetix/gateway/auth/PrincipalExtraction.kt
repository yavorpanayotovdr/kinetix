package com.kinetix.gateway.auth

import io.ktor.server.application.*
import io.ktor.server.auth.*

/**
 * Extracts the authenticated userId (JWT `sub` claim) from the principal.
 * Throws [IllegalStateException] if no principal is present — this indicates a
 * programming error (the route is not wrapped in `authenticate {}`).
 */
fun ApplicationCall.requireUserId(): String =
    principal<JwtUserPrincipal>()?.user?.userId
        ?: throw IllegalStateException("No authenticated principal — this route must be inside authenticate {}")
