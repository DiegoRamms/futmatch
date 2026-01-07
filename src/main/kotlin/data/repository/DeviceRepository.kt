package com.devapplab.data.repository

import java.util.*

interface DeviceRepository {
    fun isValidDeviceIdForUser(deviceId: UUID, userId: UUID): Boolean
    fun isTrustedDeviceIdForUser(deviceId: UUID, userId: UUID): Boolean
}

