package com.devapplab.data.repository.login_attempt

import com.devapplab.data.database.login_attempt.LoginAttemptTable
import com.devapplab.model.login_attempt.LoginAttempt
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

interface LoginAttemptRepository {
    fun findByEmail(email: String): LoginAttempt?
    fun create(email: String): LoginAttempt
    fun update(email: String, attempts: Int, lastAttemptAt: Long, lockedUntil: Long?): LoginAttempt?
    fun incrementAttempt(email: String, now: Long): LoginAttempt
    fun updateLockoutIfLater(email: String, lockUntil: Long): Boolean
    fun delete(email: String): Boolean
}

class LoginAttemptRepositoryImpl : LoginAttemptRepository {

    override fun findByEmail(email: String): LoginAttempt? {
        return LoginAttemptTable
            .selectAll()
            .where { LoginAttemptTable.email eq email }
            .firstOrNull()
            ?.toDomain()
    }

    override fun create(email: String): LoginAttempt {
        val now = System.currentTimeMillis()

        val resultRow = LoginAttemptTable.insert {
            it[this.email] = email
            it[this.attempts] = 1
            it[this.lastAttemptAt] = now
            it[this.lockedUntil] = null
            it[this.createdAt] = now
            it[this.updatedAt] = now
        }.resultedValues?.firstOrNull()
        ?: throw IllegalStateException("No ResultRow returned by insert. create LoginAttemp")

        return resultRow.toDomain()
    }

    override fun update(
        email: String,
        attempts: Int,
        lastAttemptAt: Long,
        lockedUntil: Long?
    ): LoginAttempt? {
        val updatedRows = LoginAttemptTable.update({ LoginAttemptTable.email eq email }) {
            it[this.attempts] = attempts
            it[this.lastAttemptAt] = lastAttemptAt
            it[this.lockedUntil] = lockedUntil
            it[updatedAt] = System.currentTimeMillis()
        }

        return if (updatedRows > 0) {
            findByEmail(email)
        } else {
            null
        }
    }

    override fun incrementAttempt(email: String, now: Long): LoginAttempt {
        val lockedRow = LoginAttemptTable
            .selectAll()
            .where { LoginAttemptTable.email eq email }
            .forUpdate()
            .firstOrNull()

        if (lockedRow != null) {
            val newAttempts = lockedRow[LoginAttemptTable.attempts] + 1
            LoginAttemptTable.update({ LoginAttemptTable.email eq email }) {
                it[this.attempts] = newAttempts
                it[this.lastAttemptAt] = now
                it[updatedAt] = now
            }
        } else {
            runCatching { create(email) }.getOrElse {
                // Concurrent insert won the race. Retry with row lock and explicit increment.
                val existing = LoginAttemptTable
                    .selectAll()
                    .where { LoginAttemptTable.email eq email }
                    .forUpdate()
                    .firstOrNull()
                    ?: throw IllegalStateException("LoginAttempt row missing after concurrent create for email=$email")

                val newAttempts = existing[LoginAttemptTable.attempts] + 1
                LoginAttemptTable.update({ LoginAttemptTable.email eq email }) {
                    it[this.attempts] = newAttempts
                    it[this.lastAttemptAt] = now
                    it[updatedAt] = now
                }
            }
        }

        return findByEmail(email)
            ?: throw IllegalStateException("LoginAttempt row missing after increment for email=$email")
    }

    override fun updateLockoutIfLater(email: String, lockUntil: Long): Boolean {
        val existing = LoginAttemptTable
            .selectAll()
            .where { LoginAttemptTable.email eq email }
            .forUpdate()
            .firstOrNull()
            ?: return false

        val currentLockUntil = existing[LoginAttemptTable.lockedUntil]
        if (currentLockUntil != null && currentLockUntil >= lockUntil) {
            return false
        }

        return LoginAttemptTable.update({ LoginAttemptTable.email eq email }) {
            it[this.lockedUntil] = lockUntil
            it[updatedAt] = System.currentTimeMillis()
        } > 0
    }

    override fun delete(email: String): Boolean {
        return LoginAttemptTable.deleteWhere { LoginAttemptTable.email eq email } > 0
    }

    private fun ResultRow.toDomain(): LoginAttempt {
        return LoginAttempt(
            id = this[LoginAttemptTable.id],
            email = this[LoginAttemptTable.email],
            attempts = this[LoginAttemptTable.attempts],
            lastAttemptAt = this[LoginAttemptTable.lastAttemptAt],
            lockedUntil = this[LoginAttemptTable.lockedUntil],
            createdAt = this[LoginAttemptTable.createdAt],
            updatedAt = this[LoginAttemptTable.updatedAt]
        )
    }
}
