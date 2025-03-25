package com.devapplab.model.auth

import java.util.*

data class ClaimConfig(
    val userId: UUID,
    val isEmailVerified: Boolean
)
