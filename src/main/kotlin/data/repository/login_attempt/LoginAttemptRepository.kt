package com.devapplab.data.repository.login_attempt

import com.devapplab.data.database.login_attempt.LoginAttemptDao
import com.devapplab.model.login_attempt.LoginAttempt

interface LoginAttemptRepository {
    suspend fun findByEmail(email: String): LoginAttempt?
    suspend fun create(email: String): LoginAttempt
    suspend fun update(email: String, attempts: Int, lastAttemptAt: Long, lockedUntil: Long?): LoginAttempt?
    suspend fun delete(email: String): Boolean
}

class LoginAttemptRepositoryImpl(private val dao: LoginAttemptDao) : LoginAttemptRepository {

    override suspend fun findByEmail(email: String): LoginAttempt? {
        return dao.findByEmail(email)
    }

    override suspend fun create(email: String): LoginAttempt {
        return dao.create(email)
    }

    override suspend fun update(email: String, attempts: Int, lastAttemptAt: Long, lockedUntil: Long?): LoginAttempt? {
        return dao.update(email, attempts, lastAttemptAt, lockedUntil)
    }

    override suspend fun delete(email: String): Boolean {
        return dao.delete(email)
    }
}
