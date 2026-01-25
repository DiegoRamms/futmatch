package com.devapplab.data.repository.device

import java.util.*

interface DeviceRepository {
    fun saveDevice(userId: UUID, deviceInfo: String, isTrusted: Boolean = false): UUID
    fun isValidDeviceIdForUser(deviceId: UUID, userId: UUID): Boolean
    fun isTrustedDeviceIdForUser(deviceId: UUID, userId: UUID): Boolean
    fun markDeviceAsTrusted(deviceId: UUID): Boolean
    fun changeDeviceLastUsed(deviceId: UUID): Boolean
}