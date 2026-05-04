package com.devapplab.data.repository.notification

import com.devapplab.config.dbQuery
import com.devapplab.data.database.notification.NotificationTable
import com.devapplab.model.notification.Notification
import com.devapplab.model.notification.NotificationType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.util.*

class NotificationRepositoryImpl : NotificationRepository {

    override suspend fun createNotification(
        userId: UUID,
        notificationType: NotificationType,
        title: String,
        body: String,
        metadata: String?,
    ): UUID = dbQuery {
        NotificationTable.insert {
            it[this.userId] = userId
            it[this.notificationType] = notificationType.name
            it[this.title] = title
            it[this.body] = body
            it[this.metadata] = metadata
            it[this.createdAt] = System.currentTimeMillis()
        }[NotificationTable.id]
    }

    override suspend fun getNotificationById(notificationId: UUID): Notification? = dbQuery {
        NotificationTable.selectAll().where {
            NotificationTable.id eq notificationId
        }.firstOrNull()?.toNotification()
    }

    override suspend fun deleteNotification(notificationId: UUID): Boolean = dbQuery {
        NotificationTable.deleteWhere { id eq notificationId } > 0
    }

    override suspend fun getUserNotifications(userId: UUID, limit: Int, offset: Int): List<Notification> = dbQuery {
        NotificationTable.selectAll()
            .where { NotificationTable.userId eq userId }
            .orderBy(NotificationTable.createdAt to org.jetbrains.exposed.v1.core.SortOrder.DESC)
            .limit(limit)
            .map { it.toNotification() }
    }

    override suspend fun markAsRead(notificationId: UUID): Boolean = dbQuery {
        NotificationTable.update({ NotificationTable.id eq notificationId }) {
            it[this.isRead] = true
        } > 0
    }

    private fun ResultRow.toNotification(): Notification = Notification(
        id = this[NotificationTable.id],
        userId = this[NotificationTable.userId],
        title = this[NotificationTable.title],
        body = this[NotificationTable.body],
        notificationType = NotificationType.valueOf(this[NotificationTable.notificationType]),
        createdAt = this[NotificationTable.createdAt],
        metadata = this[NotificationTable.metadata],
        isRead = this[NotificationTable.isRead],
    )
}
