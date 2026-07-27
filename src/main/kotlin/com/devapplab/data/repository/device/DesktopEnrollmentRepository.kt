package com.devapplab.data.repository.device

import com.devapplab.model.device.PendingDesktopEnrollment
import java.util.UUID

interface DesktopEnrollmentRepository {
    suspend fun createPending(request: PendingDesktopEnrollment): Boolean
    suspend fun isOrphanedDesktopDevice(deviceId: UUID): Boolean
    suspend fun findByDeviceId(deviceId: UUID, now: Long): PendingDesktopEnrollment?
    suspend fun findDetails(enrollmentId: UUID, nonce: UUID, now: Long): DesktopEnrollmentDetails?
    fun consumeAndApprove(
        enrollmentId: UUID,
        nonce: UUID,
        ownerUserId: UUID,
        approvedBy: UUID,
        now: Long
    ): Boolean
    suspend fun deleteExpired(now: Long): Int
}
