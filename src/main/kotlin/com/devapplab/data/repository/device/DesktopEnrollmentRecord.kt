package com.devapplab.data.repository.device

import com.devapplab.model.device.DesktopEnrollmentStatus
import java.util.UUID

data class DesktopEnrollmentRecord(
    val deviceId: UUID,
    val publicKey: String,
    val nonce: String,
    val label: String,
    val ownerUserId: UUID,
    val status: DesktopEnrollmentStatus,
    val expiresAt: Long
)
