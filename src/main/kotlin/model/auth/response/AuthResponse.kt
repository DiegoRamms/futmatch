package com.devapplab.model.auth.response

import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class AuthResponse(
    @Serializable(with = UUIDSerializer::class)
    val userId: UUID? = null,
    @Serializable(with = UUIDSerializer::class)
    val deviceId: UUID? = null,
    val authTokenResponse: AuthTokenResponse? = null,
    val authCode: AuthCode

)

@Serializable
data class AuthTokenResponse(
    val accessToken: String,
    val refreshToken: String? = null,
)

enum class AuthCode(val code: String) {
    SUCCESS("SUCCESS"),
    SUCCESS_NEED_MFA("SUCCESS_NEED_MFA"),
    SUCCESS_MFA("SUCCESS_MFA"),
    USER_CREATED("USER_CREATED"),
    REFRESHED_JWT("REFRESHED_JWT"),
    REFRESHED_BOTH_TOKENS("REFRESHED_BOTH_TOKENS")
}
