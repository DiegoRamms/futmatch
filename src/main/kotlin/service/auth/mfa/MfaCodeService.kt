package com.devapplab.service.auth.mfa

import com.devapplab.data.repository.MfaCodeRepository
import com.devapplab.model.mfa.MfaData
import model.mfa.MfaChannel
import java.util.*

class MfaCodeService(private val mfaCodeRepository: MfaCodeRepository) {
    suspend fun createMfaCode(
        userId: UUID,
        deviceId: UUID,
        hashedCode: String,
        channel: MfaChannel,
        expiresAt: Long
    ): UUID {
        return mfaCodeRepository.createMfaCode(userId, deviceId, hashedCode, channel, expiresAt)
    }

    suspend fun getLatestValidMfaCode(userId: UUID, deviceId: UUID): MfaData? {
        val code = mfaCodeRepository.getLatestMfaCode(userId, deviceId) ?: return null

        val isExpired = code.expiresAt < System.currentTimeMillis()
        val isAlreadyUsed = code.verified

        return if (isExpired || isAlreadyUsed) null else code
    }

}