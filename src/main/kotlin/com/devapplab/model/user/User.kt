package com.devapplab.model.user

import java.util.*


data class User(
    val id: UUID? = null,
    val name: String,
    val lastName: String,
    val email: String,
    val password: String,
    val phone: String,
    val status: UserStatus,
    val gender: Gender,
    val country: String,
    val birthDate: Long,
    val playerPosition: PlayerPosition,
    val profilePic: String?,
    val level: PlayerLevel,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val role: UserRole,
)