package com.devapplab.model.auth

import com.devapplab.model.user.UserStatus
import java.util.UUID

data class UserSignInInfo(
    val userId: UUID,
    val password: String,
    val status: UserStatus,
    val isEmailVerified: Boolean,
)
