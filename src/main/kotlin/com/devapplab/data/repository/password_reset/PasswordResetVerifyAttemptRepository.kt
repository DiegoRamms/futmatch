package com.devapplab.data.repository.password_reset

import com.devapplab.model.password_reset.PasswordResetVerifyAttempt

interface PasswordResetVerifyAttemptRepository {
    fun findByEmail(email: String): PasswordResetVerifyAttempt?
    fun create(email: String): PasswordResetVerifyAttempt
    fun incrementAttempt(email: String, now: Long): PasswordResetVerifyAttempt
    fun updateLockoutIfLater(email: String, lockUntil: Long): Boolean
    fun delete(email: String): Boolean
}
