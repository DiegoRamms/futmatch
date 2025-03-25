package com.devapplab.model.auth.response

import kotlinx.serialization.Serializable

@Serializable
data class AuthResponse(
    val authTokenResponse: AuthTokenResponse? = null,
    val authCode: AuthCode
)

@Serializable
data class AuthTokenResponse(
    val accessToken: String,
    val refreshToken: String,
)

enum class AuthCode(val code: String) {
    SUCCESS("SUCCESS"),
    USER_CREATED("USER_CREATED"),
    NEED_MFA("NEED_MFA")
}
