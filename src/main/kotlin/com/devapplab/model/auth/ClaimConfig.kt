package com.devapplab.model.auth

import com.devapplab.model.user.UserRole
import com.devapplab.model.device.DevicePlatform
import java.util.*

data class ClaimConfig(
    val userId: UUID,
    val userRole: UserRole,
    val deviceId: UUID,
    val devicePlatform: DevicePlatform?
)
