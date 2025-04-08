package com.devapplab.data.repository

import com.devapplab.model.mfa.MfaData
import model.mfa.MfaChannel
import java.util.*

interface MfaCodeRepository {
    suspend fun createMfaCode(
        userId: UUID,
        deviceId: UUID,
        hashedCode: String,
        channel: MfaChannel,
        expiresAt: Long
    ): UUID

    suspend fun getLatestMfaCode(userId: UUID, deviceId: UUID): MfaData?

}

