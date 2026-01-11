package com.devapplab.data.repository

import com.devapplab.data.database.refresh_token.RefreshTokenDao
import com.devapplab.data.database.user.UserDao
import com.devapplab.model.auth.RefreshTokenPayload
import data.database.mfa.MfaCodeDao
import data.repository.auth.AuthRepository
import data.repository.device.DeviceRepository
import model.auth.AuthUserSavedData
import model.user.User
import java.util.*


class AuthRepositoryImpl(
    private val userDao: UserDao,
    private val deviceRepository: DeviceRepository,
    private val mfaCodeDao: MfaCodeDao,
    private val refreshTokenDao: RefreshTokenDao,
) : AuthRepository {

    override fun createUserWithDevice(userWithPasswordHashed: User, deviceInfo: String): AuthUserSavedData {
        val userId = userDao.addUser(userWithPasswordHashed)
        val deviceId = deviceRepository.saveDevice(userId, deviceInfo)
        return AuthUserSavedData(userId, deviceId)
    }

    override fun createDevice(userId: UUID, deviceInfo: String, isTrusted: Boolean): UUID {
        val deviceId = deviceRepository.saveDevice(userId, deviceInfo, isTrusted)
        return deviceId
    }

    override fun completeMfaVerification(userId: UUID, deviceId: UUID, mfaCodeId: UUID) {
        userDao.markEmailAsVerified(userId)
        deviceRepository.markDeviceAsTrusted(deviceId)
        mfaCodeDao.markAsVerified(mfaCodeId)
    }

    override fun completeForgotPasswordMfaVerification(mfaCodeId: UUID) {
        mfaCodeDao.markAsVerified(mfaCodeId)
    }

    override fun rotateRefreshToken(userId: UUID, deviceId: UUID, newPayload: RefreshTokenPayload) {
        deviceRepository.changeDeviceLastUsed(deviceId)
        refreshTokenDao.saveToken(
            userId = userId,
            deviceId = deviceId,
            token = newPayload.hashedToken,
            expiresAt = newPayload.expiresAt
        )
        refreshTokenDao.revokeToken(deviceId)
    }

    override fun revokeRefreshToken(deviceId: UUID): Boolean {
        return refreshTokenDao.revokeCurrentToken(deviceId)
    }
}
