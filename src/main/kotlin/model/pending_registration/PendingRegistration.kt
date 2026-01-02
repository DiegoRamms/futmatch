package com.devapplab.model.pending_registration

import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import model.user.Gender
import model.user.PlayerLevel
import model.user.PlayerPosition
import model.user.UserRole
import java.util.*

@Serializable
data class PendingRegistration(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val name: String,
    val lastName: String,
    val email: String,
    val password: String,
    val phone: String,
    val country: String,
    val birthDate: Long,
    val playerPosition: PlayerPosition,
    val gender: Gender,
    val profilePic: String?,
    val level: PlayerLevel,
    val userRole: UserRole,
    val verificationCode: String,
    val expiresAt: Long,
    val createdAt: Long,
    val updatedAt: Long
)
