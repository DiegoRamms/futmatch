package com.devapplab.data.repository.auth

import com.devapplab.data.repository.MfaCodeRepository
import com.devapplab.data.repository.RefreshTokenRepository
import com.devapplab.data.repository.device.DeviceRepository
import com.devapplab.data.repository.user.UserRepository
import com.devapplab.model.auth.RefreshTokenPayload
import com.devapplab.model.auth.RefreshTokenStatusReason
import java.util.*

class AuthRepositoryImpl(
    private val userRepository: UserRepository,
    private val deviceRepository: DeviceRepository,
    private val mfaCodeRepository: MfaCodeRepository,
    private val refreshTokenRepository: RefreshTokenRepository
) : AuthRepository {

    override fun createDevice(userId: UUID, deviceInfo: String, isTrusted: Boolean): UUID {
        val deviceId = deviceRepository.saveDevice(userId, deviceInfo, isTrusted)
        return deviceId
    }

    override fun completeMfaVerification(userId: UUID, deviceId: UUID, mfaCodeId: UUID) {
        userRepository.markEmailAsVerified(userId)
        deviceRepository.markDeviceAsTrusted(deviceId)
        mfaCodeRepository.markAsVerified(mfaCodeId)
    }

    override fun completeForgotPasswordMfaVerification(mfaCodeId: UUID) {
        mfaCodeRepository.markAsVerified(mfaCodeId)
    }

    override fun rotateRefreshToken(userId: UUID, deviceId: UUID, newPayload: RefreshTokenPayload) {
        deviceRepository.changeDeviceLastUsed(deviceId)
        val tokenId = refreshTokenRepository.saveToken(
            userId = userId,
            deviceId = deviceId,
            token = newPayload.hashedToken,
            expiresAt = newPayload.expiresAt
        )
        refreshTokenRepository.markPreviousActiveTokensAsRotated(
            deviceId = deviceId,
            currentTokenId = tokenId,
            changedAt = System.currentTimeMillis()
        )
    }

    override fun rotateRefreshTokenIfActive(
        currentTokenId: UUID,
        userId: UUID,
        deviceId: UUID,
        newPayload: RefreshTokenPayload
    ): Boolean {
        val changedAt = System.currentTimeMillis()
        if (!refreshTokenRepository.markTokenAsRotatedIfActive(currentTokenId, changedAt)) {
            return false
        }

        deviceRepository.changeDeviceLastUsed(deviceId)
        refreshTokenRepository.saveToken(
            userId = userId,
            deviceId = deviceId,
            token = newPayload.hashedToken,
            expiresAt = newPayload.expiresAt
        )
        return true
    }

    override fun revokeRefreshToken(deviceId: UUID): Boolean {
        return refreshTokenRepository.revokeActiveTokens(
            deviceId = deviceId,
            reason = RefreshTokenStatusReason.SIGN_OUT,
            changedAt = System.currentTimeMillis()
        )
    }
}
