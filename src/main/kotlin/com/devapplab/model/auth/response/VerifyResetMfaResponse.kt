package com.devapplab.model.auth.response

import kotlinx.serialization.Serializable

@Serializable
data class VerifyResetMfaResponse(
    val resetToken: String
)
