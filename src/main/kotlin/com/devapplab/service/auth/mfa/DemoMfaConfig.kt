package com.devapplab.service.auth.mfa

import java.security.MessageDigest

/**
 * Fixed MFA code for the single App Review demo account. Disabled unless DEMO_MFA_ENABLED=true.
 */
data class DemoMfaConfig(
    val enabled: Boolean = false,
    val email: String = "",
    val code: String = ""
) {
    fun isDemoUser(userEmail: String): Boolean =
        enabled && email.isNotBlank() && email.equals(userEmail.trim(), ignoreCase = true)

    fun matches(candidate: String): Boolean =
        enabled && MessageDigest.isEqual(code.toByteArray(), candidate.toByteArray())

    companion object {
        const val CODE_LENGTH = 6
        const val EXPIRES_IN_SECONDS = 600L
        const val RESEND_AFTER_SECONDS = 30L
    }
}
