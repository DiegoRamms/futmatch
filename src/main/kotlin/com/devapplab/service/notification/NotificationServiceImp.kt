package com.devapplab.service.notification

import com.devapplab.data.repository.device.DeviceRepository
import com.devapplab.data.repository.match.MatchRepository
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

class NotificationServiceImp(
    private val deviceRepository: DeviceRepository,
    private val notificationRepository: NotificationRepository,
    private val matchRepository: MatchRepository,
) : NotificationService {
    private val logger = LoggerFactory.getLogger(this::class.java)


    override suspend fun sendPaymentAuthorizedNotification(userId: UUID, matchId: UUID, locale: Locale) {
        val matchContext = getMatchContext(matchId, locale)
        sendAndPersistNotification(
            userId = userId,
            locale = locale,
            notificationType = NotificationType.PAYMENT_AUTHORIZED,
            titleKey = StringResourcesKey.NOTIFICATION_PAYMENT_AUTHORIZED_TITLE,
            bodyKey = StringResourcesKey.NOTIFICATION_PAYMENT_AUTHORIZED_BODY,
            metadata = baseMetadata(matchId, "PAYMENT_AUTHORIZED", matchContext),
            bodySuffix = matchContext?.bodySuffix
        )
    }

    override suspend fun sendPaymentSucceededNotification(userId: UUID, matchId: UUID, locale: Locale) {
        val matchContext = getMatchContext(matchId, locale)
        sendAndPersistNotification(
            userId = userId,
            locale = locale,
            notificationType = NotificationType.PAYMENT_SUCCEEDED,
            titleKey = StringResourcesKey.NOTIFICATION_PAYMENT_SUCCEEDED_TITLE,
            bodyKey = StringResourcesKey.NOTIFICATION_PAYMENT_SUCCEEDED_BODY,
            metadata = baseMetadata(matchId, "PAYMENT_SUCCEEDED", matchContext),
            bodySuffix = matchContext?.bodySuffix
        )
    }


    override suspend fun sendPaymentFailedNotification(userId: UUID, matchId: UUID, locale: Locale) {
        val matchContext = getMatchContext(matchId, locale)
        sendAndPersistNotification(
            userId = userId,
            locale = locale,
            notificationType = NotificationType.PAYMENT_FAILED,
            titleKey = StringResourcesKey.NOTIFICATION_PAYMENT_FAILED_TITLE,
            bodyKey = StringResourcesKey.NOTIFICATION_PAYMENT_FAILED_BODY,
            metadata = baseMetadata(matchId, "PAYMENT_FAILED", matchContext),
            bodySuffix = matchContext?.bodySuffix
        )
    }

    override suspend fun sendReservationExpiredNotification(userId: UUID, matchId: UUID, fieldName: String, locale: Locale) {
        val matchContext = getMatchContext(matchId, locale)
        sendAndPersistNotification(
            userId = userId,
            locale = locale,
            notificationType = NotificationType.RESERVATION_EXPIRED,
            titleKey = StringResourcesKey.NOTIFICATION_RESERVATION_EXPIRED_TITLE,
            bodyKey = StringResourcesKey.NOTIFICATION_RESERVATION_EXPIRED_BODY,
            metadata = baseMetadata(matchId, "RESERVATION_EXPIRED", matchContext) + mapOf("fieldName" to fieldName),
            placeholders = mapOf("fieldName" to fieldName),
            bodySuffix = matchContext?.bodySuffix
        )
    }

    override suspend fun sendMatchCanceledNotification(userId: UUID, matchId: UUID, fieldName: String, locale: Locale, refundStatus: RefundStatus) {
        try {
            val matchContext = getMatchContext(matchId, locale)
            val title = locale.getString(StringResourcesKey.NOTIFICATION_MATCH_CANCELED_TITLE)
            val placeholders = mapOf("fieldName" to fieldName)

            val baseBody = when (refundStatus) {
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
            val body = appendContext(baseBody, matchContext?.bodySuffix)

            val metadataJson = convertMapToJson(
                baseMetadata(matchId, "MATCH_CANCELED", matchContext) + mapOf(
                    "matchId" to matchId.toString(),
                    "fieldName" to fieldName,
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

    override suspend fun sendMatchCompletedNotification(
        userId: UUID,
        matchId: UUID,
        fieldName: String,
        teamAScore: Int,
        teamBScore: Int,
        bestPlayerId: UUID,
        resultType: NotificationType,
        locale: Locale
    ) {
        val titleKey: StringResourcesKey
        val bodyKey: StringResourcesKey

        when (resultType) {
            NotificationType.MATCH_COMPLETED_WINNER -> {
                titleKey = StringResourcesKey.NOTIFICATION_MATCH_COMPLETED_WINNER_TITLE
                bodyKey = StringResourcesKey.NOTIFICATION_MATCH_COMPLETED_WINNER_BODY
            }
            NotificationType.MATCH_COMPLETED_WINNER_MVP -> {
                titleKey = StringResourcesKey.NOTIFICATION_MATCH_COMPLETED_WINNER_MVP_TITLE
                bodyKey = StringResourcesKey.NOTIFICATION_MATCH_COMPLETED_WINNER_MVP_BODY
            }
            NotificationType.MATCH_COMPLETED_LOSER -> {
                titleKey = StringResourcesKey.NOTIFICATION_MATCH_COMPLETED_LOSER_TITLE
                bodyKey = StringResourcesKey.NOTIFICATION_MATCH_COMPLETED_LOSER_BODY
            }
            NotificationType.MATCH_COMPLETED_DRAW -> {
                titleKey = StringResourcesKey.NOTIFICATION_MATCH_COMPLETED_DRAW_TITLE
                bodyKey = StringResourcesKey.NOTIFICATION_MATCH_COMPLETED_DRAW_BODY
            }
            else -> return
        }
        val matchContext = getMatchContext(matchId, locale)

        sendAndPersistNotification(
            userId = userId,
            locale = locale,
            notificationType = resultType,
            titleKey = titleKey,
            bodyKey = bodyKey,
            metadata = mapOf(
                "matchId" to matchId.toString(),
                "fieldName" to fieldName,
                "teamAScore" to teamAScore.toString(),
                "teamBScore" to teamBScore.toString(),
                "bestPlayerId" to bestPlayerId.toString(),
                "resultVariant" to resultType.name,
                "type" to resultType.name
            ) + (matchContext?.metadataExtras ?: emptyMap()),
            placeholders = mapOf(
                "fieldName" to fieldName,
                "score" to "$teamAScore-$teamBScore"
            ),
            bodySuffix = matchContext?.bodySuffix
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

    suspend fun sendAndPersistNotification(
        userId: UUID,
        locale: Locale,
        notificationType: NotificationType,
        titleKey: StringResourcesKey,
        bodyKey: StringResourcesKey,
        metadata: Map<String, String>? = null,
        placeholders: Map<String, String> = emptyMap(),
        bodySuffix: String? = null,
    ): AppResult<UUID> {
        return try {
            val title = locale.getString(titleKey)
            val baseBody = locale.getString(bodyKey, placeholders)
            val body = appendContext(baseBody, bodySuffix)
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

    private fun appendContext(baseBody: String, bodySuffix: String?): String {
        if (bodySuffix.isNullOrBlank()) return baseBody
        return "$baseBody $bodySuffix"
    }

    private fun baseMetadata(matchId: UUID, type: String, matchContext: MatchContext?): Map<String, String> {
        return mapOf("matchId" to matchId.toString(), "type" to type) + (matchContext?.metadataExtras ?: emptyMap())
    }

    private suspend fun getMatchContext(matchId: UUID, locale: Locale): MatchContext? {
        val match = matchRepository.getMatchById(matchId) ?: return null
        val matchDateTime = formatMatchDateTime(match.dateTime, locale)
        val fieldName = match.fieldName
        return MatchContext(
            bodySuffix = "Partido: $fieldName, $matchDateTime.",
            metadataExtras = mapOf(
                "fieldName" to fieldName,
                "matchDateTime" to matchDateTime
            )
        )
    }

    private fun formatMatchDateTime(matchDateTimeMs: Long, locale: Locale): String {
        val formatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM yyyy, HH:mm", locale)
        return Instant.ofEpochMilli(matchDateTimeMs)
            .atZone(ZoneId.of("America/Mexico_City"))
            .format(formatter)
    }

    private data class MatchContext(
        val bodySuffix: String,
        val metadataExtras: Map<String, String>
    )
}
