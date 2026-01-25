package com.devapplab.model.auth

import com.devapplab.model.user.UserRole
import java.util.*

data class ClaimConfig(
    val userId: UUID,
    val userRole: UserRole
)
