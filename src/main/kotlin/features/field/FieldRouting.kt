package com.devapplab.features.field

import com.devapplab.config.requireRole
import com.devapplab.utils.extractImageMeta
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import model.user.UserRole
import org.koin.ktor.plugin.scope
import java.io.File
import java.util.UUID
import kotlin.io.use

fun Route.fieldRouting() {
    route("fields") {
        post("create") {
            call.requireRole(UserRole.ADMIN, UserRole.BOTH)
            val fieldController = call.scope.get<FieldController>()
            fieldController.createField(call)
        }


        post("/{fieldId}/{position}/images") {
            val fieldController = call.scope.get<FieldController>()
            fieldController.addFieldImage(call)
        }

        get("image/{fieldId}/{imageName}") {
            val fieldController = call.scope.get<FieldController>()
            fieldController.getImage(call)
        }

        delete("/fields/{fieldId}/images/{imageName}") {
            val fieldId = call.parameters["fieldId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            val imageName = call.parameters["imageName"] ?: return@delete call.respond(HttpStatusCode.BadRequest)

            println("imageName: $imageName")
            val filePath = "uploads/fields/$fieldId/images/$imageName"
            val file = File(filePath)

            if (file.exists()) {
                if (file.delete()) {
                    call.respond(HttpStatusCode.OK, "Archivo eliminado correctamente")
                } else {
                    call.respond(HttpStatusCode.InternalServerError, "No se pudo eliminar el archivo")
                }
            } else {
                call.respond(HttpStatusCode.NotFound, "Archivo no encontrado")
            }
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

        post("delete/{fieldId}") {
            call.requireRole(UserRole.ADMIN, UserRole.BOTH)
            val fieldController = call.scope.get<FieldController>()
            fieldController.deleteField(call)
        }
    }
}