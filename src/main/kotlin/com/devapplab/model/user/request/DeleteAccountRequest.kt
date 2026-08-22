package com.devapplab.model.user.request

import com.devapplab.model.auth.identity.AuthProvider
import kotlinx.serialization.Serializable

/**
 * Either `password` OR (`provider` + `identityToken`, plus `nonce` for Apple) must
 * be present — a social account (`users.password == null`) has no password to
 * verify, so it re-authenticates with a fresh token from its provider instead.
 */
@Serializable
data class DeleteAccountRequest(
    val confirmation: String,
    val password: String? = null,
    val provider: AuthProvider? = null,
    val identityToken: String? = null,
    val nonce: String? = null
) {
    companion object {
        const val REQUIRED_CONFIRMATION = "DELETE_MY_ACCOUNT"
    }
}
