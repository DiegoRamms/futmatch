package com.devapplab.model.user.response

import com.devapplab.model.user.Gender
import com.devapplab.model.user.UserRole
import com.devapplab.model.user.UserStatus
import com.devapplab.model.device.DevicePlatform
import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class AdminManagedUserResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val name: String,
    val lastName: String,
    val email: String,
    val phone: String,
    val country: String,
    val birthDate: Long,
    val gender: Gender,
    val profilePic: String?,
    val role: UserRole,
    val status: UserStatus,
    val isEmailVerified: Boolean,
    val createdAt: Long
)

@Serializable
data class AdminManagedUserPageResponse(
    val items: List<AdminManagedUserResponse>,
    val page: Int,
    val pageSize: Int,
    val total: Long
)

@Serializable
data class AdminUserDetailsResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val name: String,
    val lastName: String,
    val email: String,
    val phone: String,
    val country: String,
    val birthDate: Long,
    val gender: Gender,
    val profilePic: String?,
    val role: UserRole,
    val status: UserStatus,
    val isEmailVerified: Boolean,
    val createdAt: Long,
    val account: AdminUserAccountResponse,
    val security: AdminUserSecurityResponse,
    val participation: AdminUserParticipationResponse,
    val devices: List<AdminUserDeviceResponse>
)

@Serializable
data class AdminUserAccountResponse(
    val emailVerifiedAt: Long?,
    val accessUpdatedAt: Long?
)

@Serializable
data class AdminUserSecurityResponse(
    val activeSessionCount: Long,
    val failedLoginAttempts: Int,
    val lockedUntil: Long?
)

@Serializable
data class AdminUserParticipationResponse(
    val upcomingMatchesCount: Long,
    val completedMatchesCount: Long
)

@Serializable
data class AdminUserDeviceResponse(
    @Serializable(with = UUIDSerializer::class)
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

@Serializable
data class AdminUserPaymentHistoryPageResponse(val items: List<AdminUserPaymentHistoryItemResponse>, val page: Int, val pageSize: Int, val total: Long)

@Serializable
data class AdminUserPaymentHistoryItemResponse(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val fieldName: String,
    val matchStartsAt: Long,
    val amountInCents: Long,
    val currency: String,
    val status: com.devapplab.model.payment.PaymentAttemptStatus,
    val statusUpdatedAt: Long,
    val paidAt: Long?,
    val method: AdminUserPaymentMethodResponse?,
    val refundedAt: Long?
)

@Serializable
data class AdminUserPaymentMethodResponse(val brand: String, val last4: String)
