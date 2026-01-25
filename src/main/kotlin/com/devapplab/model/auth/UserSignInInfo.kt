package com.devapplab.model.auth

import com.devapplab.model.user.UserRole
import com.devapplab.model.user.UserStatus
import java.util.*

data class UserSignInInfo(
    val userId: UUID,
    val userRole: UserRole,
    val password: String,
    val status: UserStatus,
    val isEmailVerified: Boolean,
)
