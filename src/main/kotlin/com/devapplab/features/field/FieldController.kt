package com.devapplab.features.field

import com.devapplab.config.getIdentifier
import com.devapplab.model.AppResult
import com.devapplab.model.auth.ClaimType
import com.devapplab.model.field.mapper.toField
import com.devapplab.model.field.request.CreateFieldRequest
import com.devapplab.model.field.request.FieldPricingCustomRequest
import com.devapplab.model.field.request.FieldPricingEstimateRequest
import com.devapplab.model.field.request.UpdateFieldRequest
import com.devapplab.observability.requestContext
import com.devapplab.utils.respond
import com.devapplab.utils.respondRedirect
import com.devapplab.utils.retrieveLocale
import com.devapplab.utils.toUUIDOrNull
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import java.util.*

class FieldController(
    private val fieldService: com.devapplab.service.field.FieldService
) {
    suspend fun createField(call: ApplicationCall) {
        val adminId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val request = call.receive<CreateFieldRequest>()
        val appResult = fieldService.createField(request.toField(adminId), call.requestContext())
        call.respond(appResult = appResult)
    }

    suspend fun addFieldImage(call: ApplicationCall) {
        val locale: Locale = call.retrieveLocale()
        val adminId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val fieldId = UUID.fromString(call.parameters["fieldId"])
        val position = (call.parameters["position"])?.toInt() ?: 0
        fieldService.ensureAdminAssignedToField(adminId, fieldId)
        val multipart = call.receiveMultipart()
        val appResult = fieldService.saveFieldImage(locale, fieldId, position, multipart, call.requestContext(), adminId)
        call.respond(appResult)
    }

    suspend fun updateFieldImage(call: ApplicationCall) {
        val locale: Locale = call.retrieveLocale()
        val imageId = UUID.fromString(call.parameters["imageId"])
        val fieldId = UUID.fromString(call.parameters["fieldId"])
        val adminId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        fieldService.ensureAdminAssignedToField(adminId, fieldId)
        val multipart = call.receiveMultipart()
        val appResult = fieldService.updateFieldImage(locale, imageId, multipart, call.requestContext(), adminId)
        call.respond(appResult)
    }

    suspend fun deleteFieldImage(call: ApplicationCall) {
        val locale: Locale = call.retrieveLocale()
        val imageId = UUID.fromString(call.parameters["imageId"])
        val fieldId = UUID.fromString(call.parameters["fieldId"])
        val adminId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        fieldService.ensureAdminAssignedToField(adminId, fieldId)
        val appResult = fieldService.deleteFieldImage(locale, imageId, call.requestContext(), adminId)
        call.respond(appResult)
    }

    suspend fun getImage(call: ApplicationCall) {
        val locale: Locale = call.retrieveLocale()
        val imageName = call.parameters["imageName"]
        val appResult: AppResult<String> = fieldService.getImage(locale, imageName, call.requestContext())
        call.respondRedirect(appResult)
    }

    suspend fun updateField(call: ApplicationCall) {
        val locale: Locale = call.retrieveLocale()
        val adminId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val request = call.receive<UpdateFieldRequest>()
        fieldService.ensureAdminAssignedToField(adminId, request.fieldId)
        val updatedId = fieldService.updateField(locale, request.toField(adminId), call.requestContext(), adminId)
        call.respond(updatedId)
    }

    suspend fun linkLocationToField(call: ApplicationCall) {
        val locale: Locale = call.retrieveLocale()
        val adminId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val fieldId = UUID.fromString(call.parameters["fieldId"])
        val locationId = UUID.fromString(call.parameters["locationId"])
        
        fieldService.ensureAdminAssignedToField(adminId, fieldId)
        val result = fieldService.linkLocationToField(locale, fieldId, locationId, call.requestContext(), adminId)
        call.respond(result)
    }

    suspend fun deleteField(call: ApplicationCall) {
        val locale: Locale = call.retrieveLocale()
        val adminId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val fieldId = UUID.fromString(call.parameters["fieldId"])
        fieldService.ensureAdminAssignedToField(adminId, fieldId)
        val deleted = fieldService.deleteField(locale, fieldId, call.requestContext(), adminId)
        call.respond(deleted)
    }

    suspend fun getFieldsByAdmin(call: ApplicationCall) {
        val adminId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val fields = fieldService.getFieldsByAdminId(adminId)
        call.respond(fields)
    }

    suspend fun getAllFields(call: ApplicationCall) {
        val fields = fieldService.getAllFields()
        call.respond(fields)
    }

    suspend fun getAllFieldBasics(call: ApplicationCall) {
        val fields = fieldService.getAllFieldBasics()
        call.respond(fields)
    }

    suspend fun getFieldPricingEstimate(call: ApplicationCall) {
        val locale: Locale = call.retrieveLocale()
        val fieldId = call.parameters["fieldId"]?.toUUIDOrNull()
            ?: throw NotFoundException("Can't find field id")
        val request = call.receive<FieldPricingEstimateRequest>()
        val result = fieldService.getFieldPricingEstimate(
            locale = locale,
            fieldId = fieldId,
            maxPlayers = request.maxPlayers,
            context = call.requestContext()
        )
        call.respond(result)
    }

    suspend fun getFieldCustomPricing(call: ApplicationCall) {
        val locale: Locale = call.retrieveLocale()
        val fieldId = call.parameters["fieldId"]?.toUUIDOrNull()
            ?: throw NotFoundException("Can't find field id")
        val request = call.receive<FieldPricingCustomRequest>()
        val result = fieldService.getFieldCustomPricing(
            locale = locale,
            fieldId = fieldId,
            maxPlayers = request.maxPlayers,
            pricePerPlayerInCents = request.pricePerPlayerInCents,
            context = call.requestContext()
        )
        call.respond(result)
    }
}
