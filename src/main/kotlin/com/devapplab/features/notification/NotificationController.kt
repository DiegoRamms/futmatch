package com.devapplab.features.notification

import com.devapplab.config.getIdentifier
import com.devapplab.model.auth.ClaimType
import com.devapplab.service.notification.NotificationService
import com.devapplab.utils.retrieveLocale
import com.devapplab.utils.respond
import io.ktor.server.application.*
import java.util.*

class NotificationController(private val notificationService: NotificationService) {

    suspend fun getNotifications(call: ApplicationCall) {
        val userId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val locale = call.retrieveLocale()
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
        val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

        val result = notificationService.getUserNotifications(
            userId,
            limit.coerceIn(1, 100),
            offset.coerceAtLeast(0),
            locale
        )
        call.respond(result)
    }

    suspend fun deleteNotification(call: ApplicationCall) {
        val userId = call.getIdentifier(ClaimType.USER_IDENTIFIER)
        val locale = call.retrieveLocale()
        val notificationId = UUID.fromString(call.parameters["notificationId"] ?: "")

        val result = notificationService.deleteNotification(notificationId, userId, locale)
        call.respond(result)
    }
}
