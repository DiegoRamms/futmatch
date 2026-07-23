package com.devapplab.data.repository.mfa

import com.devapplab.model.mfa.LoginMfaVerifyAttempt
import java.util.UUID

interface LoginMfaVerifyAttemptRepository {
    fun find(userId: UUID, deviceId: UUID): LoginMfaVerifyAttempt?
    fun create(userId: UUID, deviceId: UUID): LoginMfaVerifyAttempt
    fun incrementAttempt(userId: UUID, deviceId: UUID, now: Long): LoginMfaVerifyAttempt
    fun updateLockoutIfLater(userId: UUID, deviceId: UUID, lockUntil: Long): Boolean
    fun delete(userId: UUID, deviceId: UUID): Boolean
    fun deleteByUserIdTx(userId: UUID): Int
    suspend fun deleteSafe(userId: UUID, deviceId: UUID): Boolean
}
