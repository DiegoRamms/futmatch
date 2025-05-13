package com.devapplab.features.field

import com.devapplab.config.getIdentifier
import com.devapplab.model.auth.ClaimType
import com.devapplab.model.field.mapper.toField
import com.devapplab.model.field.mapper.toFieldImage
import com.devapplab.model.field.request.CreateFieldImageRequest
import com.devapplab.model.field.request.CreateFieldRequest
import com.devapplab.service.field.FieldService
import com.devapplab.utils.respond
import com.devapplab.utils.retrieveLocale
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
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
        val request = call.receive<CreateFieldImageRequest>()
        val imageId = fieldService.createFieldImage(locale, request.toFieldImage())
        call.respond(imageId)
    }

    suspend fun updateField(call: ApplicationCall) {
        val locale: Locale = call.retrieveLocale()
        val adminId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val request = call.receive<CreateFieldRequest>()
        val updatedId = fieldService.updateField(locale, request.toField(adminId))
        call.respond(updatedId)
    }

    suspend fun deleteField(call: ApplicationCall) {
        val locale: Locale = call.retrieveLocale()
        val fieldId = UUID.fromString(call.parameters["fieldId"])
        fieldService.deleteField(locale, fieldId)
        call.respond("Field deleted")
    }

    suspend fun getFieldsByAdmin(call: ApplicationCall) {
        val locale: Locale = call.retrieveLocale()
        val adminId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val fields = fieldService.getFieldsByAdminId(locale, adminId)
        call.respond(fields)
    }

}
