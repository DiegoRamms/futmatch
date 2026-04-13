package com.devapplab.data.repository

import com.devapplab.model.mfa.MfaChannel
import com.devapplab.model.mfa.MfaData
import com.devapplab.model.mfa.MfaPurpose
import java.util.*

interface MfaCodeRepository {
    fun createMfaCode(
        userId: UUID,
        deviceId: UUID?,
        hashedCode: String,
        channel: MfaChannel,
        purpose: MfaPurpose,
        expiresAt: Long
    ): UUID

    fun getRecentActiveMfaCodes(userId: UUID, deviceId: UUID?, purpose: MfaPurpose, limit: Int = 2): List<MfaData>
    fun findLatestMfaCode(userId: UUID, purpose: MfaPurpose): MfaData?
    fun findLatestMfaCodeSince(userId: UUID, purpose: MfaPurpose, since: Long): MfaData?
    fun markAsVerified(codeId: UUID): Boolean
    fun countRecentCodes(userId: UUID, purpose: MfaPurpose, since: Long): Long
    suspend fun deleteExpiredMfaCodes(): Boolean
    suspend fun deleteById(codeId: UUID): Boolean
}
