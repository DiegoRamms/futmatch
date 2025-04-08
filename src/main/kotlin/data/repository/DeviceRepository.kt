package com.devapplab.data.repository

import java.util.*

interface DeviceRepository {
    fun createDevice(userId: UUID, deviceInfo: String): UUID
    suspend fun isValidDeviceIdForUser(deviceId: UUID, userId: UUID): Boolean
    suspend fun isTrustedDeviceIdForUser(deviceId: UUID, userId: UUID): Boolean
}

