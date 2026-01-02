package com.devapplab.model.user

import kotlinx.serialization.Serializable
import model.user.Gender
import model.user.PlayerLevel
import model.user.PlayerPosition
import model.user.UserRole

@Serializable
data class PendingUser(
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
    val isEmailVerified: Boolean = true,
    val status: UserStatus = UserStatus.ACTIVE,
    val createdAt: Long,
    val updatedAt: Long
)
