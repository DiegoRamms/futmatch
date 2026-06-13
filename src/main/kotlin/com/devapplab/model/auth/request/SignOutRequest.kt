package com.devapplab.model.auth.request

import com.devapplab.utils.NullableUUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class SignOutRequest(
    @Serializable(with = NullableUUIDSerializer::class) val deviceId: UUID? = null
)
