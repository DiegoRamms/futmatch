package data.repository

import com.devapplab.config.dbQuery
import com.devapplab.data.repository.MfaCodeRepository
import com.devapplab.model.mfa.MfaData
import data.database.mfa.MfaCodeDao
import model.mfa.MfaChannel
import model.mfa.MfaPurpose
import java.util.*

class MfaCodeRepositoryImpl(
    private val mfaCodeDao: MfaCodeDao
) : MfaCodeRepository {

    override suspend fun createMfaCode(
        userId: UUID,
        deviceId:
        UUID?,
        hashedCode: String,
        channel: MfaChannel,
        purpose: MfaPurpose,
        expiresAt: Long
    ): UUID {
        return mfaCodeDao.createMfaCode(userId, deviceId, hashedCode, channel, purpose, expiresAt)
    }

    override suspend fun getLatestMfaCode(userId: UUID, deviceId: UUID?, purpose: MfaPurpose): MfaData? {
        return dbQuery { mfaCodeDao.getLatestMfaCode(userId, deviceId, purpose) }
    }

    override suspend fun deleteExpiredMfaCodes(): Boolean {
        return mfaCodeDao.deleteExpiredMFACodes()
    }

    override suspend fun deleteById(codeId: UUID): Boolean {
        return mfaCodeDao.deleteById(codeId)
    }

}