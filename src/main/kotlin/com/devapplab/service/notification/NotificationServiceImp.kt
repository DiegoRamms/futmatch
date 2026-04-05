package com.devapplab.service.notification

import com.devapplab.data.repository.device.DeviceRepository
import com.devapplab.model.match.RefundStatus
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.getString
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification
import org.slf4j.LoggerFactory
import java.util.Locale
import java.util.UUID

class NotificationServiceImp(
    private val deviceRepository: DeviceRepository
) : NotificationService {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun sendPaymentFailedNotification(userId: UUID, matchId: UUID, locale: Locale) {
        val title = locale.getString(StringResourcesKey.NOTIFICATION_PAYMENT_FAILED_TITLE)
        val body = locale.getString(StringResourcesKey.NOTIFICATION_PAYMENT_FAILED_BODY)
        
        notifyUser(
            userId = userId,
            title = title,
            body = body,
            data = mapOf("matchId" to matchId.toString(), "type" to "PAYMENT_FAILED")
        )
    }

    override suspend fun sendReservationExpiredNotification(userId: UUID, matchId: UUID, locale: Locale) {
        val title = locale.getString(StringResourcesKey.NOTIFICATION_RESERVATION_EXPIRED_TITLE)
        val body = locale.getString(StringResourcesKey.NOTIFICATION_RESERVATION_EXPIRED_BODY)

        notifyUser(
            userId = userId,
            title = title,
            body = body,
            data = mapOf("matchId" to matchId.toString(), "type" to "RESERVATION_EXPIRED")
        )
    }

    override suspend fun sendMatchCanceledNotification(userId: UUID, matchId: UUID, fieldName: String, locale: Locale, refundStatus: RefundStatus) {
        val title = when (refundStatus) {
            RefundStatus.REFUNDED -> locale.getString(StringResourcesKey.NOTIFICATION_MATCH_CANCELED_REFUND_TITLE)
            RefundStatus.FAILED -> locale.getString(StringResourcesKey.NOTIFICATION_MATCH_CANCELED_REFUND_FAILED_TITLE)
            RefundStatus.NO_CHARGE -> locale.getString(StringResourcesKey.NOTIFICATION_MATCH_CANCELED_TITLE)
        }
        
        val body = when (refundStatus) {
            RefundStatus.REFUNDED -> locale.getString(StringResourcesKey.NOTIFICATION_MATCH_CANCELED_REFUND_BODY)
            RefundStatus.FAILED -> locale.getString(StringResourcesKey.NOTIFICATION_MATCH_CANCELED_REFUND_FAILED_BODY)
            RefundStatus.NO_CHARGE -> locale.getString(StringResourcesKey.NOTIFICATION_MATCH_CANCELED_BODY)
        }

        notifyUser(
            userId = userId,
            title = title,
            body = body,
            data = mapOf(
                "matchId" to matchId.toString(),
                "fieldName" to fieldName,
                "type" to "MATCH_CANCELED",
                "refundStatus" to refundStatus.name
            )
        )
    }

    override suspend fun sendToToken(
        token: String,
        title: String,
        body: String,
        data: Map<String, String>
    ): String {
        val message = Message.builder()
            .setToken(token)
            .setNotification(
                Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build()
            )
            .putAllData(data)
            .build()

        return FirebaseMessaging.getInstance().send(message)
    }

    override suspend fun sendToTokens(
        tokens: List<String>,
        title: String,
        body: String,
        data: Map<String, String>
    ) {
        if (tokens.isEmpty()) return

        val message = MulticastMessage.builder()
            .addAllTokens(tokens)
            .setNotification(
                Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build()
            )
            .putAllData(data)
            .build()

        val response = FirebaseMessaging.getInstance().sendEachForMulticast(message)

        response.responses.forEachIndexed { index, sendResponse ->
            val token = tokens[index]

            if (!sendResponse.isSuccessful) {
                val error = sendResponse.exception
                logger.warn("Failed to send notification to token $token: ${error?.message}")
                // TODO: Invalidate token in DB if error indicates invalid token
            }
        }
    }

    override suspend fun notifyUser(
        userId: UUID,
        title: String,
        body: String,
        data: Map<String, String>
    ) {
        val tokens = deviceRepository.getActiveFcmTokensByUserId(userId)
        if (tokens.isEmpty()) {
            logger.info("No active FCM tokens found for user $userId")
            return
        }

        if (tokens.size == 1) {
            try {
                sendToToken(tokens.first(), title, body, data)
            } catch (e: Exception) {
                logger.error("Failed to send notification to user $userId", e)
            }
        } else {
            try {
                sendToTokens(tokens, title, body, data)
            } catch (e: Exception) {
                logger.error("Failed to send multicast notification to user $userId", e)
            }
        }
    }
}