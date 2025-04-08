package data.repository

import com.devapplab.config.dbQuery
import com.devapplab.data.database.refresh_token.RefreshTokenDao
import com.devapplab.data.repository.RefreshTokenRepository
import com.devapplab.model.auth.RefreshTokenPayload
import model.auth.RefreshTokenRecord
import model.auth.RefreshTokenValidationInfo
import java.util.*

class RefreshTokenRepositoryImp(private val refreshTokenDao: RefreshTokenDao) : RefreshTokenRepository {

    override suspend fun saveToken(userId: UUID, deviceId: UUID, refreshTokenPayload: RefreshTokenPayload): UUID =
        dbQuery {
            refreshTokenDao.saveToken(
                userId = userId,
                deviceId = deviceId,
                token = refreshTokenPayload.hashedToken,
                expiresAt = refreshTokenPayload.expiresAt
            )
        }

    override suspend fun findLatestTokenByUserId(userId: UUID): RefreshTokenRecord? {
        return refreshTokenDao.findByTokenByUserId(userId)
    }

    override suspend fun getValidationInfo(deviceId: UUID): RefreshTokenValidationInfo?= dbQuery {
        refreshTokenDao.getRefreshTokenValidationInfo(deviceId)
    }

}