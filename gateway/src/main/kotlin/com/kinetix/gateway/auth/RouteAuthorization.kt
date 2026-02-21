package com.kinetix.gateway.auth

import com.kinetix.common.security.Permission
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.requirePermission(permission: Permission, build: Route.() -> Unit): Route {
    val route = createChild(object : RouteSelector() {
        override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int) =
            RouteSelectorEvaluation.Transparent
    })
    route.install(
        createRouteScopedPlugin("PermissionCheck_${permission.name}") {
            on(AuthenticationChecked) { call ->
                val principal = call.principal<JwtUserPrincipal>()
                if (principal != null && !principal.user.hasPermission(permission)) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "forbidden", "message" to "Insufficient permissions"))
                }
            }
        },
    )
    route.build()
    return route
}
