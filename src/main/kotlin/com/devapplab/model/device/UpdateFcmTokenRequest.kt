package com.devapplab.model.device

import com.devapplab.utils.NullableUUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class UpdateFcmTokenRequest(
    @Serializable(with = NullableUUIDSerializer::class)
    val deviceId: UUID? = null,
    val platform: DevicePlatform,
    val fcmToken: String,
    val deviceInfo: String,
    val appVersion: String,
    val osVersion: String
)
