package com.devapplab.model.match

import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class MatchCancelResult(
    val canceled: Boolean,
    val totalPlayers: Int,
    val playersRemoved: Int,
    val paymentsCancelled: Int,
    val refundsIssued: Int,
    val refundFailures: List<RefundFailureInfo>
)

@Serializable
data class RefundFailureInfo(
    @Serializable(with = UUIDSerializer::class)
    val failureId: UUID,
    @Serializable(with = UUIDSerializer::class)
    val userId: UUID,
    @Serializable(with = UUIDSerializer::class)
    val paymentId: UUID,
    val errorMessage: String?
)
