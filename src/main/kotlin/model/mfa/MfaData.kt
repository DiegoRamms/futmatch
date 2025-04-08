package com.devapplab.model.mfa

import model.mfa.MfaChannel
import java.util.*

data class MfaData(
    val id: UUID,
    val userId: UUID,
    val deviceId: UUID,
    val hashedCode: String,
    val channel: MfaChannel,
    val expiresAt: Long,
    val verified: Boolean,
    val verifiedAt: Long?,
    val createdAt: Long
)
