package com.devapplab.model.auth.request

import kotlinx.serialization.Serializable
import model.user.Gender
import model.user.PlayerLevel
import model.user.PlayerPosition
import model.user.UserRole

@Serializable
data class RegisterUserRequest(
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
    val userRole: UserRole = UserRole.PLAYER
)