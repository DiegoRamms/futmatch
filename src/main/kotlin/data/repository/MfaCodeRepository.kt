package com.devapplab.data.repository

import com.devapplab.model.mfa.MfaData
import model.mfa.MfaChannel
import model.mfa.MfaPurpose
import java.util.*

interface MfaCodeRepository {
    suspend fun createMfaCode(
        userId: UUID,
        deviceId: UUID?,
        hashedCode: String,
        channel: MfaChannel,
        purpose: MfaPurpose,
        expiresAt: Long
    ): UUID

    suspend fun getLatestActiveMfaCode(userId: UUID, deviceId: UUID?, purpose: MfaPurpose): MfaData?
    suspend fun findLatestMfaCode(userId: UUID, purpose: MfaPurpose): MfaData?
    suspend fun findLatestMfaCodeSince(userId: UUID, purpose: MfaPurpose, since: Long): MfaData?
    suspend fun countRecentCodes(userId: UUID, purpose: MfaPurpose, since: Long): Long
    suspend fun deactivatePreviousCodes(userId: UUID, purpose: MfaPurpose): Int
    suspend fun deleteExpiredMfaCodes(): Boolean
    suspend fun deleteById(codeId: UUID): Boolean
}

