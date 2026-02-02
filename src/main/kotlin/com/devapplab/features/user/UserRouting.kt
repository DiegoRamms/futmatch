package com.devapplab.features.user

import com.devapplab.config.requireRole
import com.devapplab.model.user.UserRole
import io.ktor.server.routing.*
import org.koin.ktor.plugin.scope


fun Route.userRouting() {
    route("/user") {
        get {
            val userController = call.scope.get<UserController>()
            userController.getUserById(call)
        }

        post("/profile-pic") {
            call.requireRole(UserRole.PLAYER, UserRole.ADMIN, UserRole.ORGANIZER)
            val userController = call.scope.get<UserController>()
            userController.uploadProfilePic(call)
        }
    }
}