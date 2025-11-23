package com.devapplab.service.clean

import com.devapplab.data.repository.MfaCodeRepository
import com.devapplab.data.repository.RefreshTokenRepository

class CleanupDataService(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val mfaCodeRepository: MfaCodeRepository
) {
    suspend fun cleanupData(){
        refreshTokenRepository.deleteRevokedTokens()
        mfaCodeRepository.deleteExpiredMfaCodes()
    }
}