package com.devapplab.service.notification

import org.slf4j.LoggerFactory
import java.util.UUID

class NotificationServiceImp : NotificationService {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun sendPaymentFailedNotification(userId: UUID, matchId: UUID) {
        // TODO: Implement Firebase Cloud Messaging (FCM) logic here
        // 1. Get user's FCM token from DB (UserDeviceTable)
        // 2. Construct message payload
        // 3. Send via Firebase Admin SDK
        
        logger.info("🔔 [MOCK] Sending Push Notification to userId=$userId: 'Your payment for match $matchId failed. You have been removed from the match.'")
    }
}