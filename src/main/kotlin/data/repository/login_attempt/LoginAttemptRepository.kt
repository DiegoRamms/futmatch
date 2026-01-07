package com.devapplab.data.repository.login_attempt

import com.devapplab.data.database.login_attempt.LoginAttemptDAO
import com.devapplab.data.database.login_attempt.LoginAttemptTable
import com.devapplab.model.login_attempt.LoginAttempt

interface LoginAttemptRepository {
    fun findByEmail(email: String): LoginAttempt?
    fun create(email: String): LoginAttempt
    fun update(email: String, attempts: Int, lastAttemptAt: Long, lockedUntil: Long?): LoginAttempt?
    fun delete(email: String): Boolean
}

class LoginAttemptRepositoryImpl : LoginAttemptRepository {

    override fun findByEmail(email: String): LoginAttempt? {
        return LoginAttemptDAO
            .find { LoginAttemptTable.email eq email }
            .limit(1)
            .firstOrNull()
            ?.toDomain()
    }

    override fun create(email: String): LoginAttempt {
        val now = System.currentTimeMillis()

        val dao = LoginAttemptDAO.new {
            this.email = email
            this.attempts = 1
            this.lastAttemptAt = now
            this.lockedUntil = null
            this.createdAt = now
            this.updatedAt = now
        }

        return dao.toDomain()
    }

    override fun update(
        email: String,
        attempts: Int,
        lastAttemptAt: Long,
        lockedUntil: Long?
    ): LoginAttempt? {
        val dao = LoginAttemptDAO
            .find { LoginAttemptTable.email eq email }
            .limit(1)
            .firstOrNull()
            ?: return null

        dao.attempts = attempts
        dao.lastAttemptAt = lastAttemptAt
        dao.lockedUntil = lockedUntil
        dao.updatedAt = System.currentTimeMillis()

        return dao.toDomain()
    }

    override fun delete(email: String): Boolean {
        val dao = LoginAttemptDAO
            .find { LoginAttemptTable.email eq email }
            .limit(1)
            .firstOrNull()
            ?: return false

        dao.delete()
        return true
    }

    private fun LoginAttemptDAO.toDomain(): LoginAttempt {
        return LoginAttempt(
            id = this.id.value,
            email = this.email,
            attempts = this.attempts,
            lastAttemptAt = this.lastAttemptAt,
            lockedUntil = this.lockedUntil,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }
}