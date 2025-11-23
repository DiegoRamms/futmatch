package data.repository

import com.devapplab.config.dbQuery
import com.devapplab.data.repository.MfaCodeRepository
import com.devapplab.model.mfa.MfaData
import data.database.mfa.MfaCodeDao
import model.mfa.MfaChannel
import java.util.*

class MfaCodeRepositoryImpl(
    private val mfaCodeDao: MfaCodeDao
) : MfaCodeRepository {

    override suspend fun createMfaCode(
        userId: UUID,
        deviceId:
        UUID,
        hashedCode: String,
        channel: MfaChannel,
        expiresAt: Long
    ): UUID {
        return mfaCodeDao.createMfaCode(userId, deviceId, hashedCode, channel, expiresAt)
    }

    override suspend fun getLatestMfaCode(userId: UUID, deviceId: UUID): MfaData? {
        return dbQuery { mfaCodeDao.getLatestMfaCode(userId, deviceId) }
    }

    override suspend fun deleteExpiredMfaCodes(): Boolean {
        return mfaCodeDao.deleteExpiredMFACodes()
    }

}