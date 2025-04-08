package model.user

import com.devapplab.model.user.PlayerLevel
import com.devapplab.model.user.PlayerPosition
import com.devapplab.model.user.UserStatus
import java.util.*

data class User(
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
    val createdAt: Long,
    val updatedAt: Long
)