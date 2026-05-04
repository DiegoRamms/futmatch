package com.devapplab.model.notification

import kotlinx.serialization.Serializable

@Serializable
enum class NotificationType {
    PAYMENT_FAILED,
    RESERVATION_EXPIRED,
    MATCH_CANCELED,
}
