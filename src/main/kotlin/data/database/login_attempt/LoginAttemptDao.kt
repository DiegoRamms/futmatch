package com.devapplab.data.database.login_attempt

import com.devapplab.model.login_attempt.LoginAttempt
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

interface LoginAttemptDao {
    fun findByEmail(email: String): LoginAttempt?
    fun create(email: String): LoginAttempt
    fun update(email: String, attempts: Int, lastAttemptAt: Long, lockedUntil: Long?): LoginAttempt?
    fun delete(email: String): Boolean
}

class LoginAttemptDaoImpl : LoginAttemptDao {

    override fun findByEmail(email: String): LoginAttempt? {
        return LoginAttemptTable.selectAll().where { LoginAttemptTable.email eq email }
            .singleOrNull()
            ?.let(::toLoginAttempt)
    }

    override fun create(email: String): LoginAttempt  {
        val currentTime = System.currentTimeMillis()
       return LoginAttemptTable.insert {
            it[LoginAttemptTable.email] = email
            it[attempts] = 1
            it[lastAttemptAt] = currentTime
            it[updatedAt] = currentTime
            it[createdAt] = currentTime
        }.resultedValues!!.first().let(::toLoginAttempt)
    }


    override fun update(email: String, attempts: Int, lastAttemptAt: Long, lockedUntil: Long?): LoginAttempt? {
        LoginAttemptTable.update({ LoginAttemptTable.email eq email }) {
            it[LoginAttemptTable.attempts] = attempts
            it[LoginAttemptTable.lastAttemptAt] = lastAttemptAt
            it[LoginAttemptTable.lockedUntil] = lockedUntil
            it[updatedAt] = System.currentTimeMillis()
        }
        return findByEmail(email)
    }

    override fun delete(email: String): Boolean {
        return LoginAttemptTable.deleteWhere { LoginAttemptTable.email eq email } > 0
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
