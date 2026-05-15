package com.devapplab.service.notification

import com.devapplab.data.repository.device.DeviceRepository
import com.devapplab.data.repository.notification.NotificationRepository
import com.devapplab.model.AppResult
import com.devapplab.model.match.RefundStatus
import com.devapplab.model.notification.NotificationResponse
import com.devapplab.model.notification.NotificationType
import com.devapplab.model.notification.mapper.toResponseList
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.createError
import com.devapplab.utils.getString
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification
import io.ktor.http.*
import org.slf4j.LoggerFactory
import java.util.*

class NotificationServiceImp(
    private val deviceRepository: DeviceRepository,
    private val notificationRepository: NotificationRepository,
) : NotificationService {
    private val logger = LoggerFactory.getLogger(this::class.java)


    override suspend fun sendPaymentSucceededNotification(userId: UUID, matchId: UUID, locale: Locale) {
        sendAndPersistNotification(
            userId = userId,
            locale = locale,
            notificationType = NotificationType.PAYMENT_SUCCEEDED,
            titleKey = StringResourcesKey.NOTIFICATION_PAYMENT_SUCCEEDED_TITLE,
            bodyKey = StringResourcesKey.NOTIFICATION_PAYMENT_SUCCEEDED_BODY,
            metadata = mapOf("matchId" to matchId.toString(), "type" to "PAYMENT_SUCCEEDED")
        )
    }


    override suspend fun sendPaymentFailedNotification(userId: UUID, matchId: UUID, locale: Locale) {
        sendAndPersistNotification(
            userId = userId,
            locale = locale,
            notificationType = NotificationType.PAYMENT_FAILED,
            titleKey = StringResourcesKey.NOTIFICATION_PAYMENT_FAILED_TITLE,
            bodyKey = StringResourcesKey.NOTIFICATION_PAYMENT_FAILED_BODY,
            metadata = mapOf("matchId" to matchId.toString(), "type" to "PAYMENT_FAILED")
        )
    }

    override suspend fun sendReservationExpiredNotification(userId: UUID, matchId: UUID, fieldName: String, locale: Locale) {
        sendAndPersistNotification(
            userId = userId,
            locale = locale,
            notificationType = NotificationType.RESERVATION_EXPIRED,
            titleKey = StringResourcesKey.NOTIFICATION_RESERVATION_EXPIRED_TITLE,
            bodyKey = StringResourcesKey.NOTIFICATION_RESERVATION_EXPIRED_BODY,
            metadata = mapOf("matchId" to matchId.toString(), "fieldName" to fieldName, "type" to "RESERVATION_EXPIRED"),
            placeholders = mapOf("fieldName" to fieldName)
        )
    }

    override suspend fun sendMatchCanceledNotification(userId: UUID, matchId: UUID, fieldName: String, locale: Locale, refundStatus: RefundStatus) {
        try {
            val title = locale.getString(StringResourcesKey.NOTIFICATION_MATCH_CANCELED_TITLE)
            val placeholders = mapOf("fieldName" to fieldName)

            val body = when (refundStatus) {
                RefundStatus.REFUNDED -> locale.getString(
                    StringResourcesKey.NOTIFICATION_MATCH_CANCELED_REFUND_BODY,
                    placeholders
                )
                RefundStatus.FAILED -> locale.getString(
                    StringResourcesKey.NOTIFICATION_MATCH_CANCELED_REFUND_FAILED_BODY,
                    placeholders
                )
                RefundStatus.NO_CHARGE -> locale.getString(
                    StringResourcesKey.NOTIFICATION_MATCH_CANCELED_BODY,
                    placeholders
                )
            }

            val metadataJson = convertMapToJson(
                mapOf(
                    "matchId" to matchId.toString(),
                    "fieldName" to fieldName,
                    "type" to "MATCH_CANCELED",
                    "refundStatus" to refundStatus.name
                )
            )

            val notificationId = notificationRepository.createNotification(
                userId = userId,
                notificationType = NotificationType.MATCH_CANCELED,
                title = title,
                body = body,
                metadata = metadataJson
            )

            notifyUser(userId, title, body, emptyMap())
            AppResult.Success(notificationId)
        } catch (e: Exception) {
            logger.error("Failed to send match canceled notification to user $userId", e)
            locale.createError(
                StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY,
                HttpStatusCode.InternalServerError
            )
        }
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

    suspend fun sendAndPersistNotification(
        userId: UUID,
        locale: Locale,
        notificationType: NotificationType,
        titleKey: StringResourcesKey,
        bodyKey: StringResourcesKey,
        metadata: Map<String, String>? = null,
        placeholders: Map<String, String> = emptyMap(),
    ): AppResult<UUID> {
        return try {
            val title = locale.getString(titleKey)
            val body = locale.getString(bodyKey, placeholders)
            val metadataJson = metadata?.let { convertMapToJson(it) }

            val notificationId = notificationRepository.createNotification(
                userId = userId,
                notificationType = notificationType,
                title = title,
                body = body,
                metadata = metadataJson,
            )

            notifyUser(userId, title, body, metadata ?: emptyMap())

            AppResult.Success(notificationId)
        } catch (_: Exception) {
            locale.createError(
                StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY,
                HttpStatusCode.InternalServerError
            )
        }
    }

    override suspend fun getUserNotifications(userId: UUID, limit: Int, offset: Int, locale: Locale): AppResult<List<NotificationResponse>> {
        return try {
            val notifications = notificationRepository.getUserNotifications(userId, limit, offset)
            AppResult.Success(notifications.toResponseList())
        } catch (e: Exception) {
            logger.error("Failed to retrieve notifications for user $userId", e)
            locale.createError(
                titleKey = StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                descriptionKey = StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY,
                status = HttpStatusCode.InternalServerError
            )
        }
    }

    override suspend fun deleteNotification(notificationId: UUID, userId: UUID, locale: Locale): AppResult<Boolean> {
        return try {
            val notification = notificationRepository.getNotificationById(notificationId)
            if (notification == null || notification.userId != userId) {
                return locale.createError(
                    titleKey = StringResourcesKey.NOT_FOUND_TITLE,
                    descriptionKey = StringResourcesKey.NOT_FOUND_DESCRIPTION,
                    status = HttpStatusCode.NotFound
                )
            }

            val deleted = notificationRepository.deleteNotification(notificationId)
            AppResult.Success(deleted)
        } catch (e: Exception) {
            logger.error("Failed to delete notification $notificationId for user $userId", e)
            locale.createError(
                titleKey = StringResourcesKey.GENERIC_TITLE_ERROR_KEY,
                descriptionKey = StringResourcesKey.GENERIC_DESCRIPTION_ERROR_KEY,
                status = HttpStatusCode.InternalServerError
            )
        }
    }

    private fun convertMapToJson(map: Map<String, String>): String {
        return map.entries.joinToString(",", "{", "}") { (k, v) ->
            "\"$k\":\"${v.replace("\"", "\\\"")}\""
        }
    }
}
