package com.devapplab.data.repository.device

import com.devapplab.model.device.DevicePlatform
import java.util.*

interface DeviceRepository {
    fun saveDevice(userId: UUID, deviceInfo: String, isTrusted: Boolean = false): UUID
    fun isValidDeviceIdForUser(deviceId: UUID, userId: UUID): Boolean
    fun isTrustedDeviceIdForUser(deviceId: UUID, userId: UUID): Boolean
    fun markDeviceAsTrusted(deviceId: UUID): Boolean
    fun changeDeviceLastUsed(deviceId: UUID): Boolean
    suspend fun updateDeviceFcmToken(
        deviceId: UUID,
        platform: DevicePlatform,
        fcmToken: String,
        deviceInfo: String,
        appVersion: String,
        osVersion: String
    ): Boolean
    suspend fun getActiveFcmTokensByUserId(userId: UUID): List<String>
}