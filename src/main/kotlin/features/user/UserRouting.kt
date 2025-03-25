package com.devapplab.features.user

import io.ktor.server.routing.*
import org.koin.ktor.plugin.scope


fun Route.userRouting() {
    route("/user") {
        get("/{id}") {
            val userController = call.scope.get<UserController>()
            userController.getUserById(call)
        }
    }

}