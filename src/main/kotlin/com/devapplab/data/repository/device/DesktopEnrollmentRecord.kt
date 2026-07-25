package com.devapplab.data.repository.device

import com.devapplab.model.device.DesktopEnrollmentStatus

data class DesktopEnrollmentRecord(
    val publicKey: String,
    val status: DesktopEnrollmentStatus
)
