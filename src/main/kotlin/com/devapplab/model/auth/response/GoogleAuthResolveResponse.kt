package com.devapplab.model.auth.response

import kotlinx.serialization.Serializable

@Serializable
data class GoogleAuthResolveResponse(
    val flow: GoogleAuthFlow,
    val authResponse: AuthResponse? = null,
    val linkAttemptToken: String? = null
)

@Serializable
enum class GoogleAuthFlow {
    AUTHENTICATED,
    SIGN_UP_REQUIRED,
    LINK_REQUIRED
}
