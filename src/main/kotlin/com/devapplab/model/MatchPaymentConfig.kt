package com.devapplab.model

data class MatchPaymentConfig(
    val maxJoinPaymentWindowHours: Long,
    val captureWindowHours: Long
)
