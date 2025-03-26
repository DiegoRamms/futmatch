package data.repository

import com.devapplab.data.database.refresh_token.RefreshTokenDao
import com.devapplab.data.repository.RefreshTokenRepository
import model.auth.RefreshTokenRecord
import model.auth.RefreshTokenValidationInfo
import java.util.*

class RefreshTokenRepositoryImp(private val refreshTokenDao: RefreshTokenDao): RefreshTokenRepository {
    override suspend fun saveToken(userId: UUID, deviceId: UUID, token: String, expiresAt: Long): Boolean {
        return refreshTokenDao.saveToken(userId, deviceId, token, expiresAt)
    }

    override suspend fun findLatestTokenByUserId(userId: UUID): RefreshTokenRecord? {
        return refreshTokenDao.findByTokenByUserId(userId)
    }

    override suspend fun getValidationInfo(deviceId: UUID): RefreshTokenValidationInfo? {
        return refreshTokenDao.getRefreshTokenValidationInfo(deviceId)
    }

    override suspend fun revokeToken(deviceId: UUID): Boolean {
        return refreshTokenDao.revokeToken(deviceId)
    }
}