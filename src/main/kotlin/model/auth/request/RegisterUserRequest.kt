package com.devapplab.model.auth.request

import com.devapplab.model.user.PlayerLevel
import com.devapplab.model.user.PlayerPosition
import kotlinx.serialization.Serializable
import model.user.Gender

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
    val level: PlayerLevel
)