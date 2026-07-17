package com.devapplab.model.auth

import com.devapplab.model.user.UserRole
import java.util.*

/** Data required to validate and refresh an access token, including the current user role. */
data class RefreshTokenValidationRecord(
    val id: UUID,
    val userId: UUID,
    val deviceId: UUID,
    val expiresAt: Long,
    val createdAt: Long,
    val status: RefreshTokenStatus,
    val statusReason: RefreshTokenStatusReason?,
    val userRole: UserRole
)
