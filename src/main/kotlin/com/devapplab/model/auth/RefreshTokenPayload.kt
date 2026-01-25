package com.devapplab.model.auth

data class RefreshTokenPayload(
    val plainToken: String,
    val hashedToken: String,
    val expiresAt: Long
)
