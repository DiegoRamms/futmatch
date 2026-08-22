package com.devapplab.model.auth.request

import com.devapplab.model.user.Gender
import com.devapplab.model.user.PlayerLevel
import com.devapplab.model.user.PlayerPosition
import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * No `profilePictureSource` — Apple never returns an avatar, so every account this
 * creates starts with `profilePic = null`. `name`/`lastName` are client-supplied
 * because Apple only ever hands them over on the very first authorization; there is
 * no token claim to cross-check them against.
 */
@Serializable
data class AppleRegistrationRequest(
    val identityToken: String,
    val authorizationCode: String,
    val nonce: String,
    val name: String,
    val lastName: String,
    val phone: String,
    val country: String,
    val birthDate: Long,
    val gender: Gender,
    val playerPosition: PlayerPosition,
    val level: PlayerLevel,
    @Serializable(with = UUIDSerializer::class)
    val deviceId: UUID? = null
)
