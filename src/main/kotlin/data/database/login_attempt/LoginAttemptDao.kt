package com.devapplab.data.database.login_attempt

import com.devapplab.config.dbQuery
import com.devapplab.model.login_attempt.LoginAttempt
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

interface LoginAttemptDao {
    suspend fun findByEmail(email: String): LoginAttempt?
    suspend fun create(email: String): LoginAttempt
    suspend fun update(email: String, attempts: Int, lastAttemptAt: Long, lockedUntil: Long?): LoginAttempt?
    suspend fun delete(email: String): Boolean
}

class LoginAttemptDaoImpl : LoginAttemptDao {

    override suspend fun findByEmail(email: String): LoginAttempt? = dbQuery {
        LoginAttemptTable.selectAll().where { LoginAttemptTable.email eq email }
            .singleOrNull()
            ?.let(::toLoginAttempt)
    }

    override suspend fun create(email: String): LoginAttempt = dbQuery {
        val currentTime = System.currentTimeMillis()
        LoginAttemptTable.insert {
            it[LoginAttemptTable.email] = email
            it[attempts] = 1
            it[lastAttemptAt] = currentTime
            it[updatedAt] = currentTime
            it[createdAt] = currentTime
        }.resultedValues!!.first().let(::toLoginAttempt)
    }

    override suspend fun update(email: String, attempts: Int, lastAttemptAt: Long, lockedUntil: Long?): LoginAttempt? = dbQuery {
        LoginAttemptTable.update({ LoginAttemptTable.email eq email }) {
            it[LoginAttemptTable.attempts] = attempts
            it[LoginAttemptTable.lastAttemptAt] = lastAttemptAt
            it[LoginAttemptTable.lockedUntil] = lockedUntil
            it[updatedAt] = System.currentTimeMillis()
        }
        findByEmail(email)
    }

    override suspend fun delete(email: String): Boolean = dbQuery {
        LoginAttemptTable.deleteWhere { LoginAttemptTable.email eq email } > 0
    }

    private fun toLoginAttempt(row: ResultRow): LoginAttempt {
        return LoginAttempt(
            id = row[LoginAttemptTable.id],
            email = row[LoginAttemptTable.email],
            attempts = row[LoginAttemptTable.attempts],
            lastAttemptAt = row[LoginAttemptTable.lastAttemptAt],
            lockedUntil = row[LoginAttemptTable.lockedUntil],
            createdAt = row[LoginAttemptTable.createdAt],
            updatedAt = row[LoginAttemptTable.updatedAt]
        )
    }
}
