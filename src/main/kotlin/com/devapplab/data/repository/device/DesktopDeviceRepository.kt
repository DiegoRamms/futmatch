package com.devapplab.data.repository.device

import java.util.*

interface DesktopDeviceRepository {
    suspend fun getEnrollment(deviceId: UUID): DesktopEnrollmentRecord?
    /** Revokes desktop authorization and every active session token bound to the same device. */
    suspend fun revokeDevice(deviceId: UUID, now: Long): Boolean
    suspend fun getActivePublicKey(deviceId: UUID): String?
    /** Temporary compatibility lookup for access JWTs minted before device_platform existed. */
    suspend fun isDesktopDevice(deviceId: UUID): Boolean
    suspend fun cleanupStaleDesktopDevices(now: Long): DesktopDeviceCleanupResult
    suspend fun claimRequestNonce(deviceId: UUID, requestId: UUID, expiresAt: Long, now: Long): Boolean
}
