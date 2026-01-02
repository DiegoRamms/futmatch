package com.devapplab.service.clean

import com.devapplab.data.repository.MfaCodeRepository
import com.devapplab.data.repository.RefreshTokenRepository
import com.devapplab.data.repository.pending_registrations.PendingRegistrationRepository

class CleanupDataService(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val mfaCodeRepository: MfaCodeRepository,
    private val pendingRegistrationRepository: PendingRegistrationRepository
) {
    suspend fun cleanupData(){
        refreshTokenRepository.deleteRevokedTokens()
        mfaCodeRepository.deleteExpiredMfaCodes()
    }

    suspend fun cleanupExpiredPendingRegistrations() {
        val currentTimestamp = System.currentTimeMillis()
        pendingRegistrationRepository.deleteExpired(currentTimestamp)
    }
}