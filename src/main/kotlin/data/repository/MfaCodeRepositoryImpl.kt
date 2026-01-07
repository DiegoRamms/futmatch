package data.repository

import com.devapplab.data.repository.MfaCodeRepository
import com.devapplab.model.mfa.MfaData
import data.database.mfa.MfaCodeDao
import model.mfa.MfaChannel
import model.mfa.MfaPurpose
import java.util.*

class MfaCodeRepositoryImpl(
    private val mfaCodeDao: MfaCodeDao
) : MfaCodeRepository {

    override fun createMfaCode(
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

    override fun getLatestActiveMfaCode(userId: UUID, deviceId: UUID?, purpose: MfaPurpose): MfaData? {
        return  mfaCodeDao.getLatestActiveMfaCode(userId, deviceId, purpose)
    }

    override fun findLatestMfaCode(userId: UUID, purpose: MfaPurpose): MfaData? {
        return mfaCodeDao.findLatestMfaCode(userId, purpose)
    }

    override fun findLatestMfaCodeSince(userId: UUID, purpose: MfaPurpose, since: Long): MfaData? {
        return mfaCodeDao.findLatestMfaCodeSince(userId, purpose, since)
    }

    override fun countRecentCodes(userId: UUID, purpose: MfaPurpose, since: Long): Long {
        return mfaCodeDao.countRecentCodes(userId, purpose, since)
    }

    override fun deactivatePreviousCodes(userId: UUID, purpose: MfaPurpose): Int {
        return mfaCodeDao.deactivatePreviousCodes(userId, purpose)
    }

    override suspend fun deleteExpiredMfaCodes(): Boolean {
        return mfaCodeDao.deleteExpiredMFACodes()
    }

    override suspend fun deleteById(codeId: UUID): Boolean {
        return mfaCodeDao.deleteById(codeId)
    }

}