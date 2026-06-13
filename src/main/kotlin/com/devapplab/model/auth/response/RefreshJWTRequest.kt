package com.devapplab.model.auth.response

import com.devapplab.utils.NullableUUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class RefreshJWTRequest(
    @Serializable(with = NullableUUIDSerializer::class)
    val userId: UUID? = null,
    @Serializable(with = NullableUUIDSerializer::class)
    val deviceId: UUID? = null,
)
