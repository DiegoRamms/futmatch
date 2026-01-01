package com.devapplab.model.mfa.response

import kotlinx.serialization.Serializable

@Serializable
data class MfaSendCodeResponse(
    val newCodeSent: Boolean,
    val expiresInSeconds: Long
)
