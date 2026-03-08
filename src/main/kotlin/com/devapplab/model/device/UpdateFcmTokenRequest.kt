package com.devapplab.model.device


import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class UpdateFcmTokenRequest(
    @Serializable(with = UUIDSerializer::class)
    val deviceId: UUID,
    val platform: DevicePlatform,
    val fcmToken: String,
    val deviceInfo: String,
    val appVersion: String,
    val osVersion: String
)