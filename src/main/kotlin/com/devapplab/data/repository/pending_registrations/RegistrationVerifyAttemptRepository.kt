package com.devapplab.data.repository.pending_registrations

import com.devapplab.model.auth.RegistrationVerifyAttempt

interface RegistrationVerifyAttemptRepository {
    fun findByEmail(email: String): RegistrationVerifyAttempt?
    fun create(email: String): RegistrationVerifyAttempt
    fun incrementAttempt(email: String, now: Long): RegistrationVerifyAttempt
    fun updateLockoutIfLater(email: String, lockUntil: Long): Boolean
    fun delete(email: String): Boolean
    suspend fun deleteSafe(email: String): Boolean
}
