package com.devapplab.data.repository.mfa

import com.devapplab.config.dbQuery
import com.devapplab.data.database.mfa.LoginMfaVerifyAttemptTable
import com.devapplab.model.mfa.LoginMfaVerifyAttempt
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

class LoginMfaVerifyAttemptRepositoryImpl : LoginMfaVerifyAttemptRepository {
    private companion object {
        const val ATTEMPT_WINDOW_MS = 15 * 60 * 1000L
    }

    override fun find(userId: UUID, deviceId: UUID): LoginMfaVerifyAttempt? {
        return LoginMfaVerifyAttemptTable
            .selectAll()
            .where { LoginMfaVerifyAttemptTable.lookupKey eq key(userId, deviceId) }
            .firstOrNull()
            ?.toDomain()
    }

    override fun create(userId: UUID, deviceId: UUID): LoginMfaVerifyAttempt {
        val now = System.currentTimeMillis()
        val row = LoginMfaVerifyAttemptTable.insert {
            it[lookupKey] = key(userId, deviceId)
            it[this.userId] = userId
            it[this.deviceId] = deviceId
            it[attempts] = 1
            it[lastAttemptAt] = now
            it[lockedUntil] = null
            it[createdAt] = now
            it[updatedAt] = now
        }.resultedValues?.firstOrNull()
            ?: throw IllegalStateException("No ResultRow returned by insert. create LoginMfaVerifyAttempt")
        return row.toDomain()
    }

    override fun incrementAttempt(userId: UUID, deviceId: UUID, now: Long): LoginMfaVerifyAttempt {
        val k = key(userId, deviceId)
        val lockedRow = LoginMfaVerifyAttemptTable
            .selectAll()
            .where { LoginMfaVerifyAttemptTable.lookupKey eq k }
            .forUpdate()
            .firstOrNull()

        if (lockedRow != null) {
            val windowExpired = now - lockedRow[LoginMfaVerifyAttemptTable.lastAttemptAt] > ATTEMPT_WINDOW_MS
            val baseAttempts = if (windowExpired) 0 else lockedRow[LoginMfaVerifyAttemptTable.attempts]
            val newAttempts = baseAttempts + 1
            LoginMfaVerifyAttemptTable.update({ LoginMfaVerifyAttemptTable.lookupKey eq k }) {
                it[this.attempts] = newAttempts
                it[lastAttemptAt] = now
                if (windowExpired) {
                    it[this.lockedUntil] = null
                }
                it[updatedAt] = now
            }
        } else {
            runCatching { create(userId, deviceId) }.getOrElse {
                val existing = LoginMfaVerifyAttemptTable
                    .selectAll()
                    .where { LoginMfaVerifyAttemptTable.lookupKey eq k }
                    .forUpdate()
                    .firstOrNull()
                    ?: throw IllegalStateException("Login MFA verify row missing after concurrent create for key=$k")

                val windowExpired = now - existing[LoginMfaVerifyAttemptTable.lastAttemptAt] > ATTEMPT_WINDOW_MS
                val baseAttempts = if (windowExpired) 0 else existing[LoginMfaVerifyAttemptTable.attempts]
                val newAttempts = baseAttempts + 1
                LoginMfaVerifyAttemptTable.update({ LoginMfaVerifyAttemptTable.lookupKey eq k }) {
                    it[this.attempts] = newAttempts
                    it[lastAttemptAt] = now
                    if (windowExpired) {
                        it[this.lockedUntil] = null
                    }
                    it[updatedAt] = now
                }
            }
        }

        return find(userId, deviceId)
            ?: throw IllegalStateException("Login MFA verify row missing after increment for key=$k")
    }

    override fun updateLockoutIfLater(userId: UUID, deviceId: UUID, lockUntil: Long): Boolean {
        val k = key(userId, deviceId)
        val existing = LoginMfaVerifyAttemptTable
            .selectAll()
            .where { LoginMfaVerifyAttemptTable.lookupKey eq k }
            .forUpdate()
            .firstOrNull()
            ?: return false

        val currentLockUntil = existing[LoginMfaVerifyAttemptTable.lockedUntil]
        if (currentLockUntil != null && currentLockUntil >= lockUntil) return false

        return LoginMfaVerifyAttemptTable.update({ LoginMfaVerifyAttemptTable.lookupKey eq k }) {
            it[this.lockedUntil] = lockUntil
            it[updatedAt] = System.currentTimeMillis()
        } > 0
    }

    override fun delete(userId: UUID, deviceId: UUID): Boolean {
        return LoginMfaVerifyAttemptTable.deleteWhere {
            LoginMfaVerifyAttemptTable.lookupKey eq key(userId, deviceId)
        } > 0
    }

    override suspend fun deleteSafe(userId: UUID, deviceId: UUID): Boolean = dbQuery {
        delete(userId, deviceId)
    }

    private fun key(userId: UUID, deviceId: UUID): String = "$userId:$deviceId"

    private fun ResultRow.toDomain(): LoginMfaVerifyAttempt {
        return LoginMfaVerifyAttempt(
            id = this[LoginMfaVerifyAttemptTable.id],
            userId = this[LoginMfaVerifyAttemptTable.userId],
            deviceId = this[LoginMfaVerifyAttemptTable.deviceId],
            attempts = this[LoginMfaVerifyAttemptTable.attempts],
            lastAttemptAt = this[LoginMfaVerifyAttemptTable.lastAttemptAt],
            lockedUntil = this[LoginMfaVerifyAttemptTable.lockedUntil],
            createdAt = this[LoginMfaVerifyAttemptTable.createdAt],
            updatedAt = this[LoginMfaVerifyAttemptTable.updatedAt]
        )
    }
}
