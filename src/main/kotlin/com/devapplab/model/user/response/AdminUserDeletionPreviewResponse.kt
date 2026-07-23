package com.devapplab.model.user.response

import com.devapplab.model.user.UserRole
import com.devapplab.model.user.UserStatus
import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class AdminUserDeletionPreviewResponse(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val name: String,
    val lastName: String,
    val email: String,
    val role: UserRole,
    val status: UserStatus,
    val canDelete: Boolean,
    val blockReason: String? = null
)
