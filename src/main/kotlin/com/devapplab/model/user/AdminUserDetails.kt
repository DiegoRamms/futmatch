package com.devapplab.model.user

import com.devapplab.model.device.DevicePlatform
import java.util.UUID

data class AdminUserDetails(
    val user: UserBaseInfo,
    val emailVerifiedAt: Long?,
    val accessUpdatedAt: Long?,
    val activeSessionCount: Long,
    val failedLoginAttempts: Int,
    val lockedUntil: Long?,
    val upcomingMatchesCount: Long,
    val completedMatchesCount: Long,
    val devices: List<AdminUserDevice>
)

data class AdminUserDevice(
    val id: UUID,
    val platform: DevicePlatform?,
    val deviceInfo: String?,
    val appVersion: String?,
    val osVersion: String?,
    val isTrusted: Boolean,
    val isActive: Boolean,
    val lastUsedAt: Long,
    val createdAt: Long
)
