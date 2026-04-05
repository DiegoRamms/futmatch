package com.devapplab.model.match

import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.util.UUID

@Serializable
data class MatchRefundFailure(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @Serializable(with = UUIDSerializer::class)
    val matchId: UUID,
    val fieldName: String?,
    @Serializable(with = UUIDSerializer::class)
    val userId: UUID,
    val userName: String,
    @Serializable(with = UUIDSerializer::class)
    val paymentId: UUID,
    val providerPaymentId: String,
    @Contextual
    val amount: BigDecimal?,
    val errorMessage: String?,
    val status: RefundFailureStatus,
    val retryCount: Int,
    val createdAt: Long
)

@Serializable
data class FailedRefundResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @Serializable(with = UUIDSerializer::class)
    val matchId: UUID,
    val fieldName: String?,
    @Serializable(with = UUIDSerializer::class)
    val userId: UUID,
    val userName: String,
    @Serializable(with = UUIDSerializer::class)
    val paymentId: UUID,
    val providerPaymentId: String,
    val amountInCents: Long?,
    val errorMessage: String?,
    val status: RefundFailureStatus,
    val retryCount: Int,
    val createdAt: Long
)

@Serializable
data class RetryResult(
    @Serializable(with = UUIDSerializer::class)
    val failureId: UUID,
    val status: RefundFailureStatus,
    val retryCount: Int,
    val alreadyReimbursed: Boolean,
    val errorMessage: String?
)
