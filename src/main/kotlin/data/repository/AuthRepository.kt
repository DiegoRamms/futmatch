package com.devapplab.data.repository

import com.devapplab.config.dbQuery
import com.devapplab.data.database.device.DeviceDao
import com.devapplab.data.database.refresh_token.RefreshTokenDao
import com.devapplab.data.database.user.UserDao
import com.devapplab.model.auth.RefreshTokenPayload
import data.database.mfa.MfaCodeDao
import model.auth.AuthUserSavedData
import model.user.User
import java.util.UUID

interface AuthRepository {
    suspend fun createUserWithDevice(userWithPasswordHashed: User, deviceInfo: String): AuthUserSavedData
    suspend fun createDevice(userId: UUID, deviceInfo: String): UUID
    suspend fun completeMfaVerification(userId: UUID, deviceId: UUID, mfaCodeId: UUID)
    suspend fun completeForgotPasswordMfaVerification(mfaCodeId: UUID)
    suspend fun rotateRefreshToken(userId: UUID, deviceId: UUID, newPayload: RefreshTokenPayload)
    suspend fun revokeRefreshToken(deviceId: UUID): Boolean
}

class AuthRepositoryImpl(
    private val userDao: UserDao,
    private val deviceDao: DeviceDao,
    private val mfaCodeDao: MfaCodeDao,
    private val refreshTokenDao: RefreshTokenDao
) : AuthRepository {
    override suspend fun createUserWithDevice(userWithPasswordHashed: User, deviceInfo: String): AuthUserSavedData =
        dbQuery {
            val userId = userDao.addUser(userWithPasswordHashed)
            val deviceId = deviceDao.saveDevice(userId, deviceInfo)
            AuthUserSavedData(userId, deviceId)
        }

    override suspend fun createDevice(userId: UUID, deviceInfo: String): UUID = dbQuery {
        val deviceId = deviceDao.saveDevice(userId, deviceInfo)
        deviceId
    }

    override suspend fun completeMfaVerification(userId: UUID, deviceId: UUID, mfaCodeId: UUID) {
        dbQuery {
            userDao.markEmailAsVerified(userId)
            deviceDao.markDeviceAsTrusted(deviceId)
            mfaCodeDao.markAsVerified(mfaCodeId)
        }
    }

    override suspend fun completeForgotPasswordMfaVerification(mfaCodeId: UUID) {
        dbQuery {
            mfaCodeDao.markAsVerified(mfaCodeId)
        }
    }

    override suspend fun rotateRefreshToken(userId: UUID, deviceId: UUID, newPayload: RefreshTokenPayload) {
        dbQuery {
            deviceDao.changeDeviceLastUsed(deviceId)
            refreshTokenDao.saveToken(
                userId = userId,
                deviceId = deviceId,
                token = newPayload.hashedToken,
                expiresAt = newPayload.expiresAt
            )
            refreshTokenDao.revokeToken(deviceId)
        }
    }

    override suspend fun revokeRefreshToken(deviceId: UUID): Boolean {
        return refreshTokenDao.revokeCurrentToken(deviceId)
    }
}

