package com.devapplab.model.user

import model.user.Gender
import java.util.*

data class UserBaseInfo(
    val id: UUID,
    val name: String,
    val lastName: String,
    val email: String,
    val phone: String,
    val status: UserStatus,
    val country: String,
    val birthDate: Long,
    val gender: Gender,
    val playerPosition: PlayerPosition,
    val profilePic: String?,
    val level: PlayerLevel,
    val isEmailVerified: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

