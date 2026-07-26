package com.devapplab.data.repository.device

import com.devapplab.model.device.DesktopEnrollmentStatus

data class DesktopEnrollmentRecord(
    val publicKey: String,
    val status: DesktopEnrollmentStatus
)

data class DesktopDeviceCleanupResult(
    val deletedOrphans: Int,
    val revokedInactiveDevices: Int,
    val deletedRevokedDevices: Int
)
