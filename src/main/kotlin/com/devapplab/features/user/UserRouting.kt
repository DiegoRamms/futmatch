package com.devapplab.features.user

import com.devapplab.config.requireRole
import com.devapplab.model.user.UserRole
import io.ktor.server.routing.*
import org.koin.ktor.plugin.scope


fun Route.userRouting() {
    route("/user") {
        get("/home") {
            val userController = call.scope.get<UserController>()
            userController.getHome(call)
        }

        get {
            val userController = call.scope.get<UserController>()
            userController.getUserById(call)
        }

        post("/profile-pic") {
            call.requireRole(UserRole.PLAYER, UserRole.ADMIN, UserRole.ORGANIZER)
            val userController = call.scope.get<UserController>()
            userController.uploadProfilePic(call)
        }

        get("admin/organizers") {
            call.requireRole(UserRole.ADMIN, UserRole.ORGANIZER)
            val userController = call.scope.get<UserController>()
            userController.getOrganizers(call)
        }

        get("payments") {
            call.requireRole(UserRole.PLAYER, UserRole.ADMIN, UserRole.ORGANIZER)
            val userController = call.scope.get<UserController>()
            userController.getPaymentHistory(call)
        }

        patch("/profile/name") {
            call.requireRole(UserRole.PLAYER, UserRole.ADMIN, UserRole.ORGANIZER)
            val userController = call.scope.get<UserController>()
            userController.updateName(call)
        }

        patch("/profile/country") {
            call.requireRole(UserRole.PLAYER, UserRole.ADMIN, UserRole.ORGANIZER)
            val userController = call.scope.get<UserController>()
            userController.updateCountry(call)
        }

        patch("/profile/gender") {
            call.requireRole(UserRole.PLAYER, UserRole.ADMIN, UserRole.ORGANIZER)
            val userController = call.scope.get<UserController>()
            userController.updateGender(call)
        }

        patch("/profile/position") {
            call.requireRole(UserRole.PLAYER, UserRole.ADMIN, UserRole.ORGANIZER)
            val userController = call.scope.get<UserController>()
            userController.updatePosition(call)
        }
    }
}
