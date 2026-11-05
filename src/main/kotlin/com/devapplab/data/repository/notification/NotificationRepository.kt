package com.devapplab.data.repository.notification

import com.devapplab.model.notification.Notification
import com.devapplab.model.notification.NotificationType
import com.devapplab.model.notification.NotificationInbox
import java.util.*

data class PaymentWarningNotification(
    val userId: UUID,
    val title: String,
    val body: String,
    val metadata: String,
    val inbox: NotificationInbox,
)

interface NotificationRepository {
    fun savePaymentWarningsTx(matchId: UUID, notifications: List<PaymentWarningNotification>, sentAt: Long): Boolean
    suspend fun createNotification(
        userId: UUID,
        notificationType: NotificationType,
        title: String,
        body: String,
        metadata: String? = null,
        inbox: NotificationInbox = NotificationInbox.PLAYER,
    ): UUID

    suspend fun getNotificationById(notificationId: UUID): Notification?

    suspend fun deleteNotification(notificationId: UUID): Boolean

    suspend fun getUserNotifications(userId: UUID, limit: Int = 50, offset: Int = 0, inbox: NotificationInbox = NotificationInbox.PLAYER): List<Notification>

    suspend fun getUnreadCount(userId: UUID, inbox: NotificationInbox = NotificationInbox.PLAYER): Long

    suspend fun markAllAsRead(userId: UUID, inbox: NotificationInbox = NotificationInbox.PLAYER): Int

    suspend fun canAccessInbox(userId: UUID, inbox: NotificationInbox): Boolean

    suspend fun markAsRead(notificationId: UUID): Boolean
}
