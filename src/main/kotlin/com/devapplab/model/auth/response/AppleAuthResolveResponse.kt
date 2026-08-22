package com.devapplab.model.auth.response

import kotlinx.serialization.Serializable

@Serializable
data class AppleAuthResolveResponse(
    val flow: AppleAuthFlow,
    val authResponse: AuthResponse? = null
)

@Serializable
enum class AppleAuthFlow {
    AUTHENTICATED,
    SIGN_UP_REQUIRED
}
