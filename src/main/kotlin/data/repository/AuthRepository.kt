package com.devapplab.data.repository

import com.devapplab.config.dbQuery
import com.devapplab.data.database.device.DeviceDao
import com.devapplab.data.database.refresh_token.RefreshTokenDao
import com.devapplab.data.database.user.UserDao
import com.devapplab.model.auth.RefreshTokenPayload
import data.database.mfa.MfaCodeDao
import model.auth.AuthUserSavedData
import model.user.User
import java.util.*

interface AuthRepository {
    suspend fun createUserWithDevice(userWithPasswordHashed: User, deviceInfo: String): AuthUserSavedData
    fun createDevice(userId: UUID, deviceInfo: String, isTrusted: Boolean = false): UUID
    fun completeMfaVerification(userId: UUID, deviceId: UUID, mfaCodeId: UUID)
    fun completeForgotPasswordMfaVerification(mfaCodeId: UUID)
    fun rotateRefreshToken(userId: UUID, deviceId: UUID, newPayload: RefreshTokenPayload)
    fun revokeRefreshToken(deviceId: UUID): Boolean
}

class AuthRepositoryImpl(
    private val userDao: UserDao,
    private val deviceDao: DeviceDao,
    private val mfaCodeDao: MfaCodeDao,
    private val refreshTokenDao: RefreshTokenDao,
) : AuthRepository {
    override suspend fun createUserWithDevice(userWithPasswordHashed: User, deviceInfo: String): AuthUserSavedData =
        dbQuery {
            val userId = userDao.addUser(userWithPasswordHashed)
            val deviceId = deviceDao.saveDevice(userId, deviceInfo)
            AuthUserSavedData(userId, deviceId)
        }

    override fun createDevice(userId: UUID, deviceInfo: String, isTrusted: Boolean): UUID {
        val deviceId = deviceDao.saveDevice(userId, deviceInfo, isTrusted)
        return deviceId
    }

    override fun completeMfaVerification(userId: UUID, deviceId: UUID, mfaCodeId: UUID) {
        userDao.markEmailAsVerified(userId)
        deviceDao.markDeviceAsTrusted(deviceId)
        mfaCodeDao.markAsVerified(mfaCodeId)
    }

    override fun completeForgotPasswordMfaVerification(mfaCodeId: UUID) {
        mfaCodeDao.markAsVerified(mfaCodeId)
    }

    override fun rotateRefreshToken(userId: UUID, deviceId: UUID, newPayload: RefreshTokenPayload) {
        deviceDao.changeDeviceLastUsed(deviceId)
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
