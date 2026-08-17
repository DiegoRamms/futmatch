package com.devapplab.model.auth.request

import com.devapplab.model.user.Gender
import com.devapplab.model.user.PlayerLevel
import com.devapplab.model.user.PlayerPosition
import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class GoogleRegistrationRequest(
    val idToken: String,
    val name: String,
    val lastName: String,
    val phone: String,
    val country: String,
    val birthDate: Long,
    val gender: Gender,
    val playerPosition: PlayerPosition,
    val level: PlayerLevel,
    val profilePictureSource: GoogleProfilePictureSource,
    @Serializable(with = UUIDSerializer::class)
    val deviceId: UUID? = null
)

@Serializable
enum class GoogleProfilePictureSource {
    GOOGLE,
    CUSTOM
}
