package com.devapplab.data.repository.device

import java.util.*

interface DesktopDeviceRepository {
    suspend fun registerApprovedDevice(deviceId: UUID, publicKey: String, label: String, ownerUserId: UUID, approvedBy: UUID, now: Long): Boolean
    suspend fun getEnrollment(deviceId: UUID): DesktopEnrollmentRecord?
    /** Revokes desktop authorization and every active session token bound to the same device. */
    suspend fun revokeDevice(deviceId: UUID, now: Long): Boolean
    suspend fun getActivePublicKey(deviceId: UUID): String?
    suspend fun claimRequestNonce(deviceId: UUID, requestId: UUID, expiresAt: Long, now: Long): Boolean
}
