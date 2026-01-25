package com.devapplab.model.auth.response

import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ForgotPasswordResponse(
    @Serializable(with = UUIDSerializer::class)
    val userId: UUID,
    val newCodeSent: Boolean,
    val expiresInSeconds: Long,
    val resendCodeTimeInSeconds: Long
)
