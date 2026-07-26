package com.devapplab.model.device

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class CreateDesktopEnrollmentRequest(
    val deviceId: String,
    val publicKey: String,
    val label: String
)

/** QR approval data. The owner is always derived from the approving mobile admin JWT. */
@Serializable
data class ApproveDesktopEnrollmentRequest(val enrollmentId: String, val nonce: String)

@Serializable
data class CreateDesktopEnrollmentResponse(
    val enrollmentId: String,
    val deviceId: String,
    val nonce: String,
    val expiresAt: Long
)

data class PendingDesktopEnrollment(
    val id: UUID,
    val deviceId: UUID,
    val publicKey: String,
    val label: String,
    val nonce: UUID,
    val expiresAt: Long,
    val createdAt: Long
)

/** Ktor-derived values used to prove possession of the new desktop private key. */
data class DesktopEnrollmentCreationProof(
    val timestamp: String?,
    val requestId: String?,
    val signature: String?,
    val method: String,
    val path: String
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
enum class DesktopEnrollmentStatus { PENDING, ACTIVE, REENROLLMENT_REQUIRED }

@Serializable
data class DesktopEnrollmentStatusResponse(val status: DesktopEnrollmentStatus)
