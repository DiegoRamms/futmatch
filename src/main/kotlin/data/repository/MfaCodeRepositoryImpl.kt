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

    override suspend fun getLatestActiveMfaCode(userId: UUID, deviceId: UUID?, purpose: MfaPurpose): MfaData? {
        return dbQuery { mfaCodeDao.getLatestActiveMfaCode(userId, deviceId, purpose) }
    }

    override suspend fun findLatestMfaCode(userId: UUID, purpose: MfaPurpose): MfaData? {
        return dbQuery { mfaCodeDao.findLatestMfaCode(userId, purpose) }
    }

    override suspend fun findLatestMfaCodeSince(userId: UUID, purpose: MfaPurpose, since: Long): MfaData? {
        return dbQuery {   mfaCodeDao.findLatestMfaCodeSince(userId, purpose, since) }
    }

    override suspend fun countRecentCodes(userId: UUID, purpose: MfaPurpose, since: Long): Long {
        return mfaCodeDao.countRecentCodes(userId, purpose, since)
    }

    override suspend fun deactivatePreviousCodes(userId: UUID, purpose: MfaPurpose): Int {
        return mfaCodeDao.deactivatePreviousCodes(userId, purpose)
    }

    override suspend fun deleteExpiredMfaCodes(): Boolean {
        return mfaCodeDao.deleteExpiredMFACodes()
    }

    override suspend fun deleteById(codeId: UUID): Boolean {
        return mfaCodeDao.deleteById(codeId)
    }

}