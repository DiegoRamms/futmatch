package com.devapplab.data.repository.login_attempt

import com.devapplab.data.database.login_attempt.LoginAttemptDao
import com.devapplab.model.login_attempt.LoginAttempt

interface LoginAttemptRepository {
    fun findByEmail(email: String): LoginAttempt?
    fun create(email: String): LoginAttempt
    fun update(email: String, attempts: Int, lastAttemptAt: Long, lockedUntil: Long?): LoginAttempt?
    fun delete(email: String): Boolean
}

class LoginAttemptRepositoryImpl(private val dao: LoginAttemptDao) : LoginAttemptRepository {

    override fun findByEmail(email: String): LoginAttempt? {
        return dao.findByEmail(email)
    }

    override  fun create(email: String): LoginAttempt {
        return dao.create(email)
    }

    override fun update(email: String, attempts: Int, lastAttemptAt: Long, lockedUntil: Long?): LoginAttempt? {
        return dao.update(email, attempts, lastAttemptAt, lockedUntil)
    }

    override fun delete(email: String): Boolean {
        return dao.delete(email)
    }
}
