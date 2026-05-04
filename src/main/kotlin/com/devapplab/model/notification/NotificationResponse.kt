package com.devapplab.model.notification

import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class NotificationResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val title: String,
    val body: String,
    val notificationType: String,
    val createdAt: Long,
    val metadata: String? = null,
    val isRead: Boolean = false,
)
