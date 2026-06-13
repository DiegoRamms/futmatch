package com.devapplab.service.clean

import com.devapplab.data.database.executor.DbExecutor
import com.devapplab.data.repository.MfaCodeRepository
import com.devapplab.data.repository.RefreshTokenRepository
import com.devapplab.data.repository.mfa.LoginMfaChallengeRepository
import com.devapplab.data.repository.pending_registrations.PendingRegistrationRepository

class CleanupDataService(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val mfaCodeRepository: MfaCodeRepository,
    private val loginMfaChallengeRepository: LoginMfaChallengeRepository,
    private val pendingRegistrationRepository: PendingRegistrationRepository,
    private val dbExecutor: DbExecutor
) {
    suspend fun cleanupData() {
        val currentTimestamp = System.currentTimeMillis()
        refreshTokenRepository.deleteRevokedTokens()
        mfaCodeRepository.deleteExpiredMfaCodes()
        loginMfaChallengeRepository.deleteInactive(currentTimestamp)
    }

    suspend fun cleanupExpiredPendingRegistrations() {
        val currentTimestamp = System.currentTimeMillis()
        dbExecutor.tx {
            pendingRegistrationRepository.deleteExpired(currentTimestamp)
        }
    }
}
