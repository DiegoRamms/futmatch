package com.devapplab.model.auth

import com.devapplab.model.user.UserStatus
import model.user.UserRole
import java.util.UUID

data class UserSignInInfo(
    val userId: UUID,
    val userRole: UserRole,
    val password: String,
    val status: UserStatus,
    val isEmailVerified: Boolean,
)
