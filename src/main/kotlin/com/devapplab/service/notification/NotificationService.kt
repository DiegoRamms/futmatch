package com.devapplab.service.notification

import com.devapplab.model.match.RefundStatus
import java.util.Locale
import java.util.UUID

interface NotificationService {
    suspend fun sendPaymentFailedNotification(userId: UUID, matchId: UUID, locale: Locale)
    suspend fun sendReservationExpiredNotification(userId: UUID, matchId: UUID, locale: Locale)
    suspend fun sendMatchCanceledNotification(userId: UUID, matchId: UUID, fieldName: String, locale: Locale, refundStatus: RefundStatus)
    suspend fun sendToToken(token: String, title: String, body: String, data: Map<String, String> = emptyMap()): String
    suspend fun sendToTokens(tokens: List<String>, title: String, body: String, data: Map<String, String> = emptyMap())
    suspend fun notifyUser(userId: UUID, title: String, body: String, data: Map<String, String> = emptyMap())
}