package com.devapplab.service.notification

import com.devapplab.model.AppResult
import com.devapplab.model.match.RefundStatus
import com.devapplab.model.notification.NotificationType
import java.util.*

interface NotificationService {
    // Send notifications
    suspend fun sendPaymentAuthorizedNotification(userId: UUID, matchId: UUID, locale: Locale)
    suspend fun sendPaymentSucceededNotification(userId: UUID, matchId: UUID, locale: Locale)
    suspend fun sendPaymentFailedNotification(userId: UUID, matchId: UUID, locale: Locale)
    suspend fun sendReservationExpiredNotification(userId: UUID, matchId: UUID, fieldName: String, locale: Locale)
    suspend fun sendMatchCanceledNotification(userId: UUID, matchId: UUID, fieldName: String, locale: Locale, refundStatus: RefundStatus)
    suspend fun sendMatchCompletedNotification(
        userId: UUID,
        matchId: UUID,
        fieldName: String,
        teamAScore: Int,
        teamBScore: Int,
        bestPlayerId: UUID,
        resultType: NotificationType,
        locale: Locale
    )
    suspend fun sendToToken(token: String, title: String, body: String, data: Map<String, String> = emptyMap()): String
    suspend fun sendToTokens(tokens: List<String>, title: String, body: String, data: Map<String, String> = emptyMap())
    suspend fun sendToTopic(topic: String, title: String, body: String, data: Map<String, String> = emptyMap()): String
    suspend fun notifyUser(userId: UUID, title: String, body: String, data: Map<String, String> = emptyMap())

    // Retrieve and manage notifications
    suspend fun getUserNotifications(userId: UUID, limit: Int = 50, offset: Int = 0, locale: Locale): AppResult<List<com.devapplab.model.notification.NotificationResponse>>
    suspend fun deleteNotification(notificationId: UUID, userId: UUID, locale: Locale): AppResult<Boolean>
}
