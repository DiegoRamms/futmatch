package com.devapplab.model.device

import kotlinx.serialization.Serializable

@Serializable
data class CreateDesktopEnrollmentRequest(
    val deviceId: String,
    val publicKey: String,
    val label: String,
    val ownerUserId: String,
    val nonce: String,
    val expiresAt: Long
)

@Serializable
enum class DesktopEnrollmentStatus { PENDING, ACTIVE, REJECTED, EXPIRED }

@Serializable
data class DesktopEnrollmentRequestResponse(
    val deviceId: String,
    val label: String,
    val ownerUserId: String,
    val status: DesktopEnrollmentStatus,
    val expiresAt: Long
)

@Serializable
data class DesktopEnrollmentStatusResponse(val status: DesktopEnrollmentStatus)
