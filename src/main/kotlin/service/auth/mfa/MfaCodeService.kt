package com.devapplab.service.auth.mfa

import com.devapplab.data.repository.MfaCodeRepository
import com.devapplab.model.mfa.MfaData
import model.mfa.MfaChannel
import model.mfa.MfaPurpose
import java.util.*

class MfaCodeService(private val mfaCodeRepository: MfaCodeRepository) {
    suspend fun createMfaCode(
        userId: UUID,
        deviceId: UUID?,
        hashedCode: String,
        channel: MfaChannel,
        purpose: MfaPurpose,
        expiresAt: Long
    ): UUID {
        return mfaCodeRepository.createMfaCode(userId, deviceId, hashedCode, channel, purpose, expiresAt)
    }

    suspend fun getLatestValidMfaCode(userId: UUID, deviceId: UUID?, purpose: MfaPurpose): MfaData? {
        val code = mfaCodeRepository.getLatestMfaCode(userId, deviceId, purpose) ?: return null
        val isExpired = code.expiresAt < System.currentTimeMillis()
        val isAlreadyUsed = code.verified

        return if (isExpired || isAlreadyUsed) null else code
    }


    suspend fun createPasswordResetMfaCode(
        userId: UUID,
        hashedCode: String,
        channel: MfaChannel,
        expiresAt: Long
    ): UUID? {

        // Check for an existing code for this purpose, regardless of its validity
        val existingCode = mfaCodeRepository.getLatestMfaCode(userId, null, MfaPurpose.PASSWORD_RESET)

        if (existingCode != null) {
            val isExpired = existingCode.expiresAt < System.currentTimeMillis()
            val isAlreadyUsed = existingCode.verified
            // If the code is still valid, do nothing to prevent spamming.

            if (!isExpired && !isAlreadyUsed) {
                return null
            } else {
                // If the code is expired or used, delete it before creating a new one.
                mfaCodeRepository.deleteById(existingCode.id)
            }

        }

        return mfaCodeRepository.createMfaCode(
            userId = userId,
            deviceId = null,
            hashedCode = hashedCode,
            channel = channel,
            purpose = MfaPurpose.PASSWORD_RESET,
            expiresAt = expiresAt
        )
    }

}

    