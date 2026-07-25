package com.devapplab.model.device

import kotlinx.serialization.Serializable

/** Desktop-generated QR payload. The owner is always derived from the approving mobile admin JWT. */
@Serializable
data class ApproveDesktopEnrollmentRequest(
    val deviceId: String,
    val publicKey: String,
    val label: String,
    val nonce: String,
    val expiresAt: Long
)

/** Ktor-derived values used to verify a pre-login desktop status poll. */
data class DesktopEnrollmentStatusProof(
    val deviceId: String?,
    val timestamp: String?,
    val requestId: String?,
    val signature: String?,
    val method: String,
    val path: String
)

@Suppress("unused")
@Serializable
enum class DesktopEnrollmentStatus { PENDING, ACTIVE, REJECTED, EXPIRED }

@Serializable
data class DesktopEnrollmentStatusResponse(val status: DesktopEnrollmentStatus)
