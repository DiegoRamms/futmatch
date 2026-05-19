package com.devapplab.features.profile

import com.devapplab.config.getIdentifier
import com.devapplab.model.auth.ClaimType
import com.devapplab.service.ProfileService
import com.devapplab.utils.createError
import com.devapplab.utils.respond
import com.devapplab.utils.retrieveLocale
import com.devapplab.utils.toUUIDOrNull
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall

class ProfileController(
    private val service: ProfileService
) {
    suspend fun getMe(call: ApplicationCall) {
        val locale = call.retrieveLocale()
        val userId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val result = service.getMyProfile(userId, locale)
        call.respond(result)
    }

    suspend fun getByUserId(call: ApplicationCall) {
        val locale = call.retrieveLocale()
        val userId = call.parameters["userId"]?.toUUIDOrNull()
            ?: run {
                call.respond(locale.createError(status = HttpStatusCode.BadRequest))
                return
            }
        val result = service.getPublicProfile(userId, locale)
        call.respond(result)
    }
}
