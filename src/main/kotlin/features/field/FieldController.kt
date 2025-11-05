package com.devapplab.features.field

import com.devapplab.config.getIdentifier
import com.devapplab.model.auth.ClaimType
import com.devapplab.model.field.mapper.toField
import com.devapplab.model.field.request.CreateFieldRequest
import com.devapplab.service.field.FieldService
import com.devapplab.utils.respond
import com.devapplab.utils.respondImage
import com.devapplab.utils.retrieveLocale
import com.devapplab.utils.toUUIDOrNull
import io.ktor.server.application.*
import io.ktor.server.request.*
import model.field.request.UpdateFieldRequest
import java.util.*

class FieldController(
    private val fieldService: FieldService
) {
    suspend fun createField(call: ApplicationCall) {
        val adminId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val request = call.receive<CreateFieldRequest>()
        val appResult = fieldService.createField(request.toField(adminId))
        call.respond(appResult = appResult)
    }

    suspend fun addFieldImage(call: ApplicationCall) {
        val locale: Locale = call.retrieveLocale()
        val fieldId = UUID.fromString(call.parameters["fieldId"])
        val position = (call.parameters["position"])?.toInt() ?: 0
        val multipart = call.receiveMultipart()
        val appResult = fieldService.saveFieldImage(locale, fieldId, position, multipart)
        call.respond(appResult)
    }

    suspend fun updateFieldImage(call: ApplicationCall){
        val locale: Locale = call.retrieveLocale()
        val imageId = UUID.fromString(call.parameters["imageId"])
        val multipart = call.receiveMultipart()
        val appResult = fieldService.updateFieldImage(locale,imageId, multipart)
        call.respond(appResult)
    }

    suspend fun getImage(call: ApplicationCall) {
        val locale: Locale = call.retrieveLocale()
        val imageName = call.parameters["imageName"]
        val fieldId = call.parameters["fieldId"]

        println("imageName: $imageName")
        val appResult = fieldService.getImage(locale, fieldId?.toUUIDOrNull(), imageName)

        call.respondImage(appResult)
    }

    suspend fun updateField(call: ApplicationCall) {
        val locale: Locale = call.retrieveLocale()
        val adminId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val request = call.receive<UpdateFieldRequest>()
        val updatedId = fieldService.updateField(locale, request.toField(adminId), adminId)
        call.respond(updatedId)
    }

    suspend fun deleteField(call: ApplicationCall) {
        val locale: Locale = call.retrieveLocale()
        val adminId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val fieldId = UUID.fromString(call.parameters["fieldId"])
        val deleted = fieldService.deleteField(locale, fieldId, adminId)
        call.respond(deleted)
    }

    suspend fun getFieldsByAdmin(call: ApplicationCall) {
        val adminId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val fields = fieldService.getFieldsByAdminId(adminId)
        call.respond(fields)
    }

}
