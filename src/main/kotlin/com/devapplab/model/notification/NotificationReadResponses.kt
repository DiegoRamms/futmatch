package com.devapplab.model.notification

import kotlinx.serialization.Serializable

@Serializable
data class UnreadNotificationCountResponse(val unreadCount: Long)

@Serializable
data class MarkAllNotificationsReadResponse(val markedAsReadCount: Int)
