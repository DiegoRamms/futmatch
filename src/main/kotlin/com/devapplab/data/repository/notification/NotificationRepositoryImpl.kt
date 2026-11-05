package com.devapplab.data.repository.notification

import com.devapplab.config.dbQuery
import com.devapplab.data.database.notification.NotificationTable
import com.devapplab.model.notification.Notification
import com.devapplab.model.notification.NotificationType
import com.devapplab.model.notification.NotificationInbox
import com.devapplab.data.database.user.UserTable
import com.devapplab.data.database.match.MatchTable
import com.devapplab.model.match.MatchStatus
import com.devapplab.model.user.UserRole
import com.devapplab.model.user.UserStatus
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import java.util.*

class NotificationRepositoryImpl : NotificationRepository {

    override fun savePaymentWarningsTx(matchId: UUID, notifications: List<PaymentWarningNotification>, sentAt: Long): Boolean {
        require(notifications.isNotEmpty())
        require(notifications.map { it.userId }.distinct().size == notifications.size)
        val match = MatchTable.select(MatchTable.id, MatchTable.status, MatchTable.paymentWindowWarningSentAt)
            .where { MatchTable.id eq matchId }.forUpdate().singleOrNull() ?: return false
        if (match[MatchTable.status] != MatchStatus.SCHEDULED || match[MatchTable.paymentWindowWarningSentAt] != null) return false

        notifications.forEach { notification ->
            NotificationTable.insert {
                it[NotificationTable.userId] = notification.userId
                it[NotificationTable.notificationType] = NotificationType.MATCH_PAYMENT_WINDOW_WARNING.name
                it[NotificationTable.title] = notification.title
                it[NotificationTable.body] = notification.body
                it[NotificationTable.metadata] = notification.metadata
                it[NotificationTable.inbox] = notification.inbox.name
                it[NotificationTable.createdAt] = sentAt
            }
        }
        MatchTable.update({ MatchTable.id eq matchId }) { it[MatchTable.paymentWindowWarningSentAt] = sentAt }
        return true
    }

    override suspend fun createNotification(
        userId: UUID,
        notificationType: NotificationType,
        title: String,
        body: String,
        metadata: String?,
        inbox: NotificationInbox,
    ): UUID = dbQuery {
        NotificationTable.insert {
            it[this.userId] = userId
            it[this.notificationType] = notificationType.name
            it[this.inbox] = inbox.name
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

    override suspend fun getUserNotifications(userId: UUID, limit: Int, offset: Int, inbox: NotificationInbox): List<Notification> = dbQuery {
        NotificationTable.selectAll()
            .where { (NotificationTable.userId eq userId) and (NotificationTable.inbox eq inbox.name) }
            .orderBy(NotificationTable.createdAt to org.jetbrains.exposed.v1.core.SortOrder.DESC)
            .limit(limit)
            .offset(offset.toLong())
            .map { it.toNotification() }
    }

    override suspend fun getUnreadCount(userId: UUID, inbox: NotificationInbox): Long = dbQuery {
        NotificationTable.selectAll().where {
            (NotificationTable.userId eq userId) and (NotificationTable.inbox eq inbox.name) and (NotificationTable.isRead eq false)
        }.count()
    }

    override suspend fun markAllAsRead(userId: UUID, inbox: NotificationInbox): Int = dbQuery {
        NotificationTable.update({
            (NotificationTable.userId eq userId) and (NotificationTable.inbox eq inbox.name) and (NotificationTable.isRead eq false)
        }) {
            it[isRead] = true
        }
    }

    override suspend fun canAccessInbox(userId: UUID, inbox: NotificationInbox): Boolean = dbQuery {
        val requiredRole = when (inbox) {
            NotificationInbox.PLAYER -> return@dbQuery true
            NotificationInbox.ADMIN -> UserRole.ADMIN
            NotificationInbox.ORGANIZER -> UserRole.ORGANIZER
        }
        UserTable.select(UserTable.id).where {
            (UserTable.id eq userId) and (UserTable.role eq requiredRole) and (UserTable.status eq UserStatus.ACTIVE)
        }.count() > 0
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
        inbox = NotificationInbox.valueOf(this[NotificationTable.inbox]),
    )
}
