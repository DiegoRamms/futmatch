package com.devapplab.model.auth.identity

import java.util.UUID

enum class AuthProvider {
    GOOGLE
}

data class AuthIdentity(
    val id: UUID? = null,
    val userId: UUID,
    val provider: AuthProvider,
    val issuer: String,
    val providerSubject: String,
    val createdAt: Long,
    val lastAuthenticatedAt: Long
)
