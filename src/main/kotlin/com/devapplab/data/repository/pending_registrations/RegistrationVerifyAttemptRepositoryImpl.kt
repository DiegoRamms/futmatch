package com.devapplab.data.repository.pending_registrations

import com.devapplab.config.dbQuery
import com.devapplab.data.database.pending_registrations.RegistrationVerifyAttemptTable
import com.devapplab.model.auth.RegistrationVerifyAttempt
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class RegistrationVerifyAttemptRepositoryImpl : RegistrationVerifyAttemptRepository {
    private companion object {
        const val ATTEMPT_WINDOW_MS = 15 * 60 * 1000L
    }

    override fun findByEmail(email: String): RegistrationVerifyAttempt? {
        return RegistrationVerifyAttemptTable
            .selectAll()
            .where { RegistrationVerifyAttemptTable.email eq email }
            .firstOrNull()
            ?.toDomain()
    }

    override fun create(email: String): RegistrationVerifyAttempt {
        val now = System.currentTimeMillis()
        val row = RegistrationVerifyAttemptTable.insert {
            it[this.email] = email
            it[attempts] = 1
            it[lastAttemptAt] = now
            it[lockedUntil] = null
            it[createdAt] = now
            it[updatedAt] = now
        }.resultedValues?.firstOrNull()
            ?: throw IllegalStateException("No ResultRow returned by insert. create RegistrationVerifyAttempt")
        return row.toDomain()
    }

    override fun incrementAttempt(email: String, now: Long): RegistrationVerifyAttempt {
        val lockedRow = RegistrationVerifyAttemptTable
            .selectAll()
            .where { RegistrationVerifyAttemptTable.email eq email }
            .forUpdate()
            .firstOrNull()

        if (lockedRow != null) {
            val windowExpired = now - lockedRow[RegistrationVerifyAttemptTable.lastAttemptAt] > ATTEMPT_WINDOW_MS
            val baseAttempts = if (windowExpired) 0 else lockedRow[RegistrationVerifyAttemptTable.attempts]
            val newAttempts = baseAttempts + 1
            RegistrationVerifyAttemptTable.update({ RegistrationVerifyAttemptTable.email eq email }) {
                it[this.attempts] = newAttempts
                it[lastAttemptAt] = now
                if (windowExpired) {
                    it[this.lockedUntil] = null
                }
                it[updatedAt] = now
            }
        } else {
            runCatching { create(email) }.getOrElse {
                val existing = RegistrationVerifyAttemptTable
                    .selectAll()
                    .where { RegistrationVerifyAttemptTable.email eq email }
                    .forUpdate()
                    .firstOrNull()
                    ?: throw IllegalStateException("Registration verify row missing after concurrent create for email=$email")

                val windowExpired = now - existing[RegistrationVerifyAttemptTable.lastAttemptAt] > ATTEMPT_WINDOW_MS
                val baseAttempts = if (windowExpired) 0 else existing[RegistrationVerifyAttemptTable.attempts]
                val newAttempts = baseAttempts + 1
                RegistrationVerifyAttemptTable.update({ RegistrationVerifyAttemptTable.email eq email }) {
                    it[this.attempts] = newAttempts
                    it[lastAttemptAt] = now
                    if (windowExpired) {
                        it[this.lockedUntil] = null
                    }
                    it[updatedAt] = now
                }
            }
        }

        return findByEmail(email)
            ?: throw IllegalStateException("Registration verify row missing after increment for email=$email")
    }

    override fun updateLockoutIfLater(email: String, lockUntil: Long): Boolean {
        val existing = RegistrationVerifyAttemptTable
            .selectAll()
            .where { RegistrationVerifyAttemptTable.email eq email }
            .forUpdate()
            .firstOrNull()
            ?: return false

        val currentLockUntil = existing[RegistrationVerifyAttemptTable.lockedUntil]
        if (currentLockUntil != null && currentLockUntil >= lockUntil) return false

        return RegistrationVerifyAttemptTable.update({ RegistrationVerifyAttemptTable.email eq email }) {
            it[this.lockedUntil] = lockUntil
            it[updatedAt] = System.currentTimeMillis()
        } > 0
    }

    override fun delete(email: String): Boolean {
        return RegistrationVerifyAttemptTable.deleteWhere { RegistrationVerifyAttemptTable.email eq email } > 0
    }

    override suspend fun deleteSafe(email: String): Boolean = dbQuery {
        delete(email)
    }

    private fun ResultRow.toDomain(): RegistrationVerifyAttempt {
        return RegistrationVerifyAttempt(
            id = this[RegistrationVerifyAttemptTable.id],
            email = this[RegistrationVerifyAttemptTable.email],
            attempts = this[RegistrationVerifyAttemptTable.attempts],
            lastAttemptAt = this[RegistrationVerifyAttemptTable.lastAttemptAt],
            lockedUntil = this[RegistrationVerifyAttemptTable.lockedUntil],
            createdAt = this[RegistrationVerifyAttemptTable.createdAt],
            updatedAt = this[RegistrationVerifyAttemptTable.updatedAt]
        )
    }
}
