package com.devapplab.service.notification

import java.util.UUID

interface NotificationService {
    suspend fun sendPaymentFailedNotification(userId: UUID, matchId: UUID)
}