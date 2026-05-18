package com.devapplab.model.notification

import kotlinx.serialization.Serializable

@Serializable
enum class NotificationType {
    PAYMENT_SUCCEEDED,
    PAYMENT_FAILED,
    RESERVATION_EXPIRED,
    MATCH_CANCELED,
    MATCH_COMPLETED_WINNER,
    MATCH_COMPLETED_WINNER_MVP,
    MATCH_COMPLETED_LOSER,
    MATCH_COMPLETED_DRAW,
}
