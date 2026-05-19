package com.devapplab.features.profile

import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.koin.ktor.plugin.scope

fun Route.profileRouting() {
    route("/profiles") {
        get("/me") {
            val profileController = call.scope.get<ProfileController>()
            profileController.getMe(call)
        }

        get("/{userId}") {
            val profileController = call.scope.get<ProfileController>()
            profileController.getByUserId(call)
        }
    }
}
