package com.devapplab.model.login_attempt

import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class LoginAttempt(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val email: String,
    val attempts: Int,
    val lastAttemptAt: Long,
    val lockedUntil: Long?,
    val createdAt: Long,
    val updatedAt: Long
)
