package com.devapplab.model.notification.mapper

import com.devapplab.model.notification.Notification
import com.devapplab.model.notification.NotificationResponse

fun Notification.toResponse(): NotificationResponse {
    return NotificationResponse(
        id = this.id,
        title = this.title,
        body = this.body,
        notificationType = this.notificationType.name,
        createdAt = this.createdAt,
        metadata = this.metadata,
        isRead = this.isRead
    )
}

fun List<Notification>.toResponseList(): List<NotificationResponse> {
    return this.map { it.toResponse() }
}
