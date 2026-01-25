package com.devapplab.service.auth.mfa

import com.devapplab.data.repository.MfaCodeRepository
import com.devapplab.model.mfa.MfaChannel
import com.devapplab.model.mfa.MfaCreationResult
import com.devapplab.model.mfa.MfaData
import com.devapplab.model.mfa.MfaPurpose
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

class MfaCodeService(private val mfaCodeRepository: MfaCodeRepository) {
    fun createMfaCode(
        userId: UUID,
        deviceId: UUID?,
        hashedCode: String,
        channel: MfaChannel,
        purpose: MfaPurpose,
        expiresAt: Long,
        config: MfaRateLimitConfig
    ): MfaCreationResult {

        // 1) Cooldown
        val latest = mfaCodeRepository.findLatestMfaCode(userId, purpose)
        if (latest != null) {
            val secondsSinceLast = ((System.currentTimeMillis() - latest.createdAt) / 1000).coerceAtLeast(0)
            if (secondsSinceLast < config.minWaitSeconds) {
                val retryAfter = config.minWaitSeconds - secondsSinceLast
                return MfaCreationResult.Cooldown(retryAfter)
            }
        }

        // 2) Lockout
        val windowStart = Instant.now()
            .minus(config.timeWindowHours, ChronoUnit.HOURS)
            .toEpochMilli()

        val recentAttempts = mfaCodeRepository.countRecentCodes(userId, purpose, windowStart)

        if (recentAttempts >= config.maxAttempts) {
            val lastInWindow = mfaCodeRepository.findLatestMfaCodeSince(userId, purpose, windowStart)
            if (lastInWindow != null) {
                val lockReleaseTime = Instant.ofEpochMilli(lastInWindow.createdAt)
                    .plus(config.lockDurationMinutes.toLong(), ChronoUnit.MINUTES)

                if (Instant.now().isBefore(lockReleaseTime)) {
                    return MfaCreationResult.Locked(config.lockDurationMinutes)
                }
            }
        }

        mfaCodeRepository.deactivatePreviousCodes(userId, purpose)

        val newId = mfaCodeRepository.createMfaCode(
            userId = userId,
            deviceId = deviceId,
            hashedCode = hashedCode,
            channel = channel,
            purpose = purpose,
            expiresAt = expiresAt
        )

        val expiresInSeconds = ((expiresAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
        return MfaCreationResult.Created(newId, expiresInSeconds)
    }

     fun getLatestValidMfaCode(userId: UUID, deviceId: UUID?, purpose: MfaPurpose): MfaData? {
        val code = mfaCodeRepository.getLatestActiveMfaCode(userId, deviceId, purpose) ?: return null
        val isExpired = code.expiresAt < System.currentTimeMillis()
        val isAlreadyUsed = code.verified

        return if (isExpired || isAlreadyUsed) null else code
    }
}

    