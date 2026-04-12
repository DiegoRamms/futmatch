package com.devapplab.model.auth.response

import kotlinx.serialization.Serializable

@Serializable
data class ForgotPasswordResponse(
    val newCodeSent: Boolean,
    val expiresInSeconds: Long,
    val resendCodeTimeInSeconds: Long
)
