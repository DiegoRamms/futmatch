package com.devapplab.data.repository.notification

import com.devapplab.model.notification.Notification
import com.devapplab.model.notification.NotificationType
import java.util.*

interface NotificationRepository {
    suspend fun createNotification(
        userId: UUID,
        notificationType: NotificationType,
        title: String,
        body: String,
        metadata: String? = null,
    ): UUID

    suspend fun getNotificationById(notificationId: UUID): Notification?

    suspend fun deleteNotification(notificationId: UUID): Boolean

    suspend fun getUserNotifications(userId: UUID, limit: Int = 50, offset: Int = 0): List<Notification>

    suspend fun markAsRead(notificationId: UUID): Boolean
}
