package com.devapplab.service.auth.mfa

data class MfaRateLimitConfig(
    val minWaitSeconds: Long,
    val maxAttempts: Int,
    val timeWindowHours: Long,
    val lockDurationMinutes: Int
)
