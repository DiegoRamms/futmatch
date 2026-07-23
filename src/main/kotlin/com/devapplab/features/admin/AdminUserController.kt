package com.devapplab.features.admin

import com.devapplab.config.getIdentifier
import com.devapplab.model.auth.ClaimType
import com.devapplab.model.user.request.UpdateManagedUserAccessRequest
import com.devapplab.model.user.request.AdminDeleteUserRequest
import com.devapplab.observability.requestContext
import com.devapplab.service.AdminUserService
import com.devapplab.utils.respond
import com.devapplab.utils.retrieveLocale
import com.devapplab.utils.toUUIDOrNull
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.receive

class AdminUserController(private val service: AdminUserService) {
    suspend fun getManagedUsers(call: ApplicationCall) {
        val pageValue = call.request.queryParameters["page"]
        val pageSizeValue = call.request.queryParameters["pageSize"]
        val page = pageValue?.toIntOrNull() ?: if (pageValue == null) 1 else INVALID_PAGE_VALUE
        val pageSize = pageSizeValue?.toIntOrNull() ?: if (pageSizeValue == null) DEFAULT_PAGE_SIZE else INVALID_PAGE_VALUE
        val result = service.getManagedUsers(
            page = page,
            pageSize = pageSize,
            roleValues = call.request.queryParameters.getAll("roles"),
            statusValues = call.request.queryParameters.getAll("statuses"),
            locale = call.retrieveLocale()
        )
        call.respond(result)
    }

    suspend fun updateManagedUserAccess(call: ApplicationCall) {
        val targetUserId = call.parameters["userId"]?.toUUIDOrNull()
            ?: throw NotFoundException("Managed user was not found")
        val request = call.receive<UpdateManagedUserAccessRequest>()
        val adminId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val result = service.updateManagedUserAccess(
            adminId = adminId,
            targetUserId = targetUserId,
            request = request,
            locale = call.retrieveLocale(),
            context = call.requestContext()
        )
        call.respond(result)
    }

    suspend fun getDeletionPreview(call: ApplicationCall) {
        val targetUserId = call.parameters["userId"]?.toUUIDOrNull()
            ?: throw NotFoundException("Managed user was not found")
        val result = service.getDeletionPreview(call.getIdentifier(ClaimType.USER_IDENTIFIER), targetUserId, call.retrieveLocale())
        call.respond(result)
    }

    suspend fun deleteUser(call: ApplicationCall) {
        val targetUserId = call.parameters["userId"]?.toUUIDOrNull()
            ?: throw NotFoundException("Managed user was not found")
        val request = call.receive<AdminDeleteUserRequest>()
        val result = service.deleteUser(
            call.getIdentifier(ClaimType.USER_IDENTIFIER), targetUserId, request.password,
            call.retrieveLocale(), call.requestContext()
        )
        call.respond(result)
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val INVALID_PAGE_VALUE = 0
    }
}
