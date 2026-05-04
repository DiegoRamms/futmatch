package com.devapplab.model.notification

import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class Notification(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @Serializable(with = UUIDSerializer::class)
    val userId: UUID,
    val title: String,
    val body: String,
    val notificationType: NotificationType,
    val createdAt: Long,
    val metadata: String? = null,
    val isRead: Boolean = false,
)
