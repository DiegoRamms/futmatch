package com.devapplab.model.auth.response

import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class AuthResponse(
    val authTokenResponse: AuthTokenResponse? = null,
    val authCode: AuthCode
)

@Serializable
data class AuthTokenResponse(
    val accessToken: String,
    val refreshToken: String? = null,
    @Serializable(with = UUIDSerializer::class)
    val deviceId: UUID? = null
)

enum class AuthCode(val code: String) {
    SUCCESS("SUCCESS"),
    USER_CREATED("USER_CREATED"),
    NEED_MFA("NEED_MFA"),
    REFRESHED_JWT("REFRESHED_JWT"),
    REFRESHED_BOTH_TOKENS("REFRESHED_BOTH_TOKENS")
}
