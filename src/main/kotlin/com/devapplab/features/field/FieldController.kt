package com.devapplab.features.field

import com.devapplab.config.getIdentifier
import com.devapplab.model.auth.ClaimType
import com.devapplab.model.field.mapper.toField
import com.devapplab.model.field.request.CreateFieldRequest
import com.devapplab.model.field.request.UpdateFieldRequest
import com.devapplab.utils.respond
import com.devapplab.utils.respondImage
import com.devapplab.utils.retrieveLocale
import com.devapplab.utils.toUUIDOrNull
import io.ktor.server.application.*
import io.ktor.server.request.*
import java.util.*

class FieldController(
    private val fieldService: com.devapplab.service.field.FieldService
) {
    suspend fun createField(call: ApplicationCall) {
        val adminId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val request = call.receive<CreateFieldRequest>()
        val appResult = fieldService.createField(request.toField(adminId))
        call.respond(appResult = appResult)
    }

    suspend fun addFieldImage(call: ApplicationCall) {
        val locale: Locale = call.retrieveLocale()
        val adminId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val fieldId = UUID.fromString(call.parameters["fieldId"])
        val position = (call.parameters["position"])?.toInt() ?: 0
        fieldService.ensureAdminAssignedToField(adminId, fieldId)
        val multipart = call.receiveMultipart()
        val appResult = fieldService.saveFieldImage(locale, fieldId, position, multipart)
        call.respond(appResult)
    }

    suspend fun updateFieldImage(call: ApplicationCall) {
        val locale: Locale = call.retrieveLocale()
        val imageId = UUID.fromString(call.parameters["imageId"])
        val fieldId = UUID.fromString(call.parameters["fieldId"])
        val adminId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        fieldService.ensureAdminAssignedToField(adminId, fieldId)
        val multipart = call.receiveMultipart()
        val appResult = fieldService.updateFieldImage(locale, imageId, multipart)
        call.respond(appResult)
    }

    suspend fun deleteFieldImage(call: ApplicationCall) {
        val locale: Locale = call.retrieveLocale()
        val imageId = UUID.fromString(call.parameters["imageId"])
        val fieldId = UUID.fromString(call.parameters["fieldId"])
        val adminId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        fieldService.ensureAdminAssignedToField(adminId, fieldId)
        val appResult = fieldService.deleteFieldImage(locale, imageId)
        call.respond(appResult)
    }

    suspend fun getImage(call: ApplicationCall) {
        val locale: Locale = call.retrieveLocale()
        val imageName = call.parameters["imageName"]
        val fieldId = call.parameters["fieldId"]
        val appResult = fieldService.getImage(locale, fieldId?.toUUIDOrNull(), imageName)
        call.respondImage(appResult)
    }

    suspend fun updateField(call: ApplicationCall) {
        val locale: Locale = call.retrieveLocale()
        val adminId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val request = call.receive<UpdateFieldRequest>()
        fieldService.ensureAdminAssignedToField(adminId, request.fieldId)
        val updatedId = fieldService.updateField(locale, request.toField(adminId))
        call.respond(updatedId)
    }

    suspend fun deleteField(call: ApplicationCall) {
        val locale: Locale = call.retrieveLocale()
        val adminId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val fieldId = UUID.fromString(call.parameters["fieldId"])
        fieldService.ensureAdminAssignedToField(adminId, fieldId)
        val deleted = fieldService.deleteField(locale, fieldId)
        call.respond(deleted)
    }

    suspend fun getFieldsByAdmin(call: ApplicationCall) {
        val adminId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val fields = fieldService.getFieldsByAdminId(adminId)
        call.respond(fields)
    }
}
