package com.devapplab.data.repository.login_attempt

import com.devapplab.data.database.login_attempt.LoginAttemptTable
import com.devapplab.model.login_attempt.LoginAttempt
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

interface LoginAttemptRepository {
    fun findByEmail(email: String): LoginAttempt?
    fun create(email: String): LoginAttempt
    fun update(email: String, attempts: Int, lastAttemptAt: Long, lockedUntil: Long?): LoginAttempt?
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