package com.devapplab.model.auth.request

import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class AppleAuthResolveRequest(
    val identityToken: String,
    val nonce: String,
    @Serializable(with = UUIDSerializer::class)
    val deviceId: UUID? = null
)
