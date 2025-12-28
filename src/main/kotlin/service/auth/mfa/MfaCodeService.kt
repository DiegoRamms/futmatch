package com.devapplab.service.auth.mfa

import com.devapplab.data.repository.MfaCodeRepository
import com.devapplab.model.mfa.MfaData
import model.mfa.MfaChannel
import model.mfa.MfaCreationResult
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


    private val passwordResetCooldownSeconds = 60

    suspend fun createPasswordResetMfaCode(
        userId: UUID,
        hashedCode: String,
        channel: MfaChannel,
        expiresAt: Long
    ): MfaCreationResult {

        val existingCode = mfaCodeRepository.getLatestMfaCode(userId, null, MfaPurpose.PASSWORD_RESET)

        if (existingCode != null) {
            val isExpired = existingCode.expiresAt < System.currentTimeMillis()
            val isAlreadyUsed = existingCode.verified

            // If a valid code exists, check if we are in the cooldown period.
            if (!isExpired && !isAlreadyUsed) {
                val timeSinceCreation = (System.currentTimeMillis() - existingCode.createdAt) / 1000
                if (timeSinceCreation < passwordResetCooldownSeconds) {
                    val retryAfter = passwordResetCooldownSeconds - timeSinceCreation
                    return MfaCreationResult.Cooldown(retryAfter)
                }
            }

            // In all other cases (expired, used, or valid but past cooldown), delete the old code.
            mfaCodeRepository.deleteById(existingCode.id)
        }

        // Proceed to create a new code.
        val newCodeId = mfaCodeRepository.createMfaCode(
            userId = userId,
            deviceId = null,
            hashedCode = hashedCode,
            channel = channel,
            purpose = MfaPurpose.PASSWORD_RESET,
            expiresAt = expiresAt
        )
        val expiresInSeconds = (expiresAt - System.currentTimeMillis()) / 1000
        return MfaCreationResult.Created(newCodeId, expiresInSeconds)
    }

}

    