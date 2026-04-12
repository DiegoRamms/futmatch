package com.devapplab.model.mfa

import kotlinx.serialization.Serializable

@Serializable
data class VerifyResetMfaRequest(
    val email: String,
    val code: String
)
