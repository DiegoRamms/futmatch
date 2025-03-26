package model.auth

import java.util.*

data class RefreshTokenRecord(
    val id: UUID,
    val userId: UUID,
    val deviceId: UUID,
    val token: String,
    val expiresAt: Long,
    val createdAt: Long,
    val ipAddress: String?,
    val userAgent: String?,
    val revoked: Boolean
)
