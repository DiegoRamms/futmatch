package com.devapplab.data.repository

import java.util.*

interface DeviceRepository {
    suspend fun isValidDeviceIdForUser(deviceId: UUID, userId: UUID): Boolean
    suspend fun isTrustedDeviceIdForUser(deviceId: UUID, userId: UUID): Boolean
}

