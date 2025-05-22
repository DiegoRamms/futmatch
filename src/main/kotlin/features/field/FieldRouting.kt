package com.devapplab.features.field

import com.devapplab.config.requireRole
import io.ktor.server.routing.*
import model.user.UserRole
import org.koin.ktor.plugin.scope

fun Route.fieldRouting() {
    route("fields") {
        post("create") {
            call.requireRole(UserRole.ADMIN, UserRole.BOTH)
            val fieldController = call.scope.get<FieldController>()
            fieldController.createField(call)
        }
        get("by-admin") {
            call.requireRole(UserRole.ADMIN, UserRole.BOTH)
            val fieldController = call.scope.get<FieldController>()
            fieldController.getFieldsByAdmin(call)
        }
    }
}