package com.devapplab.model.training

import com.devapplab.model.payment.PaymentAttemptStatus
import kotlinx.serialization.Serializable

@Serializable
enum class TrainingStatus { DRAFT, PUBLISHED, CANCELLED }

@Serializable
data class TrainingRequest(
    val fieldId: String,
    val title: String,
    val coachName: String,
    val description: String = "",
    val startsAt: Long,
    val endsAt: Long,
    val capacity: Int,
    val priceInCents: Long
)

@Serializable
data class TrainingResponse(
    val id: String,
    val fieldId: String,
    val fieldName: String,
    val address: String?,
    val latitude: Double?,
    val longitude: Double?,
    val title: String,
    val coachName: String,
    val description: String,
    val startsAt: Long,
    val endsAt: Long,
    val capacity: Int,
    val priceInCents: Long,
    val occupied: Int = 0,
    val enrollmentStatus: PaymentAttemptStatus? = null,
    val termsLocked: Boolean = false,
    val cancellationPending: Boolean = false,
    val registrationOpensAt: Long? = null,
    val freeCancellationEndsAt: Long? = null,
    val reservationExpiresAt: Long? = null,
    val status: TrainingStatus
)
