package com.devapplab.model.auth.response

import kotlinx.serialization.Serializable

@Serializable
data class SocialLinkStartResponse(
    val newCodeSent: Boolean,
    val expiresInSeconds: Long,
    val resendCodeTimeInSeconds: Long
)
