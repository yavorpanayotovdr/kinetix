package com.kinetix.gateway.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.kinetix.common.security.Role
import com.kinetix.common.security.UserPrincipal
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*

data class JwtUserPrincipal(val user: UserPrincipal) : Principal

fun Application.configureJwtAuth(config: JwtConfig) {
    install(Authentication) {
        jwt("auth-jwt") {
            realm = config.realm
            verifier(
                JWT.require(Algorithm.HMAC256(config.secret))
                    .withAudience(config.audience)
                    .withIssuer(config.issuer)
                    .build()
            )
            validate { credential ->
                if (credential.payload.audience.contains(config.audience)) {
                    val userId = credential.payload.subject ?: return@validate null
                    val username = credential.payload.getClaim("preferred_username")?.asString() ?: userId
                    val rolesClaim = credential.payload.getClaim("roles")?.asList(String::class.java) ?: emptyList()
                    val roles = rolesClaim.mapNotNull { roleName ->
                        runCatching { Role.valueOf(roleName) }.getOrNull()
                    }.toSet()
                    JwtUserPrincipal(UserPrincipal(userId, username, roles))
                } else {
                    null
                }
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "unauthorized", "message" to "Token is not valid or has expired"))
            }
        }
    }
}
