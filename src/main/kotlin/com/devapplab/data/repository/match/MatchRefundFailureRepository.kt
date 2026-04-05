package com.devapplab.data.repository.match

import com.devapplab.model.match.MatchRefundFailure
import com.devapplab.model.match.RefundFailureStatus
import java.util.UUID

interface MatchRefundFailureRepository {
    suspend fun createFailure(
        matchId: UUID,
        userId: UUID,
        paymentId: UUID,
        providerPaymentId: String,
        errorMessage: String?
    ): UUID

    suspend fun getAllFailures(): List<MatchRefundFailure>

    suspend fun getFailureById(failureId: UUID): MatchRefundFailure?

    suspend fun updateFailure(
        failureId: UUID,
        errorMessage: String?,
        status: RefundFailureStatus,
        retryCount: Int
    ): Boolean

    suspend fun deleteFailure(failureId: UUID): Boolean

    suspend fun markAsResolved(failureId: UUID): Boolean
}
