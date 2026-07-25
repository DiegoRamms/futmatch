package com.devapplab.data.repository.device

import java.util.*

interface DesktopDeviceRepository {
    suspend fun createEnrollment(record: DesktopEnrollmentRecord, submittedBy: UUID, createdAt: Long)
    suspend fun getPendingEnrollments(now: Long): List<DesktopEnrollmentRecord>
    suspend fun getEnrollment(deviceId: UUID): DesktopEnrollmentRecord?
    suspend fun approveEnrollment(deviceId: UUID, approvedBy: UUID, now: Long): Boolean
    suspend fun rejectEnrollment(deviceId: UUID, rejectedBy: UUID, now: Long): Boolean
    suspend fun revokeDevice(deviceId: UUID, now: Long): Boolean
    suspend fun getActivePublicKey(deviceId: UUID): String?
    suspend fun claimRequestNonce(deviceId: UUID, requestId: UUID, expiresAt: Long, now: Long): Boolean
}
