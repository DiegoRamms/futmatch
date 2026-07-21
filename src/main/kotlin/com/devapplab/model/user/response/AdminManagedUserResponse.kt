package com.devapplab.model.user.response

import com.devapplab.model.user.Gender
import com.devapplab.model.user.UserRole
import com.devapplab.model.user.UserStatus
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
