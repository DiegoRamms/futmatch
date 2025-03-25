package com.devapplab.model.user

import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
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
    val playerPosition: PlayerPosition,
    val profilePic: String?,
    val level: PlayerLevel,
    val createdAt: Long,
    val updatedAt: Long
)

