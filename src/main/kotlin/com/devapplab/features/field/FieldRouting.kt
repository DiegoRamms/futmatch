package com.devapplab.features.field

import com.devapplab.config.requireRole
import com.devapplab.model.user.UserRole
import io.ktor.server.routing.*
import org.koin.ktor.plugin.scope

fun Route.fieldRouting() {
    route("fields") {
        post("create") {
            call.requireRole(UserRole.ADMIN, UserRole.BOTH)
            val fieldController = call.scope.get<FieldController>()
            fieldController.createField(call)
        }

        post("/{fieldId}/{position}/images") {
            call.requireRole(UserRole.ADMIN, UserRole.BOTH)
            val fieldController = call.scope.get<FieldController>()
            fieldController.addFieldImage(call)
        }

        get("image/{fieldId}/{imageName}") {
            val fieldController = call.scope.get<FieldController>()
            fieldController.getImage(call)
        }

        post("/image/{fieldId}/{imageId}") {
            call.requireRole(UserRole.ADMIN, UserRole.BOTH)
            val fieldController = call.scope.get<FieldController>()
            fieldController.updateFieldImage(call)
        }

        delete("/delete/image/{fieldId}/{imageId}") {
            call.requireRole(UserRole.ADMIN, UserRole.BOTH)
            val fieldController = call.scope.get<FieldController>()
            fieldController.deleteFieldImage(call)
        }

        get("by-admin") {
            call.requireRole(UserRole.ADMIN, UserRole.BOTH)
            val fieldController = call.scope.get<FieldController>()
            fieldController.getFieldsByAdmin(call)
        }

        post("update") {
            call.requireRole(UserRole.ADMIN, UserRole.BOTH)
            val fieldController = call.scope.get<FieldController>()
            fieldController.updateField(call)
        }

        delete ("delete/{fieldId}") {
            call.requireRole(UserRole.ADMIN, UserRole.BOTH)
            val fieldController = call.scope.get<FieldController>()
            fieldController.deleteField(call)
        }
    }
}