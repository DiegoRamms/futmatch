package com.devapplab.model.mfa

import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class MfaCodeVerificationRequest(
    val challengeToken: String? = null,
    @Serializable(with = UUIDSerializer::class)
    val userId: UUID? = null,
    @Serializable(with = UUIDSerializer::class)
    val deviceId: UUID? = null,
    val code: String
)
