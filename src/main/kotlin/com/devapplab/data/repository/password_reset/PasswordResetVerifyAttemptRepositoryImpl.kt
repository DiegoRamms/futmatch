package com.devapplab.data.repository.password_reset

import com.devapplab.config.dbQuery
import com.devapplab.data.database.password_reset.PasswordResetVerifyAttemptTable
import com.devapplab.model.password_reset.PasswordResetVerifyAttempt
import com.devapplab.service.pii.PiiCrypto
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class PasswordResetVerifyAttemptRepositoryImpl(private val piiCrypto: PiiCrypto) : PasswordResetVerifyAttemptRepository {
    private companion object {
        const val ATTEMPT_WINDOW_MS = 15 * 60 * 1000L
    }

    override fun findByEmail(email: String): PasswordResetVerifyAttempt? {
        return PasswordResetVerifyAttemptTable
            .selectAll()
            .where { PasswordResetVerifyAttemptTable.emailLookup eq lookup(email) }
            .firstOrNull()
            ?.toDomain()
    }

    override fun create(email: String): PasswordResetVerifyAttempt {
        val now = System.currentTimeMillis()

        val row = PasswordResetVerifyAttemptTable.insert {
            it[this.emailLookup] = lookup(email)
            it[this.attempts] = 1
            it[this.lastAttemptAt] = now
            it[this.lockedUntil] = null
            it[this.createdAt] = now
            it[this.updatedAt] = now
        }.resultedValues?.firstOrNull()
            ?: throw IllegalStateException("No ResultRow returned by insert. create PasswordResetVerifyAttempt")

        return row.toDomain()
    }

    override fun incrementAttempt(email: String, now: Long): PasswordResetVerifyAttempt {
        val lockedRow = PasswordResetVerifyAttemptTable
            .selectAll()
            .where { PasswordResetVerifyAttemptTable.emailLookup eq lookup(email) }
            .forUpdate()
            .firstOrNull()

        if (lockedRow != null) {
            val windowExpired = now - lockedRow[PasswordResetVerifyAttemptTable.lastAttemptAt] > ATTEMPT_WINDOW_MS
            val baseAttempts = if (windowExpired) 0 else lockedRow[PasswordResetVerifyAttemptTable.attempts]
            val newAttempts = baseAttempts + 1
            PasswordResetVerifyAttemptTable.update({ PasswordResetVerifyAttemptTable.emailLookup eq lookup(email) }) {
                it[this.attempts] = newAttempts
                it[this.lastAttemptAt] = now
                if (windowExpired) {
                    it[this.lockedUntil] = null
                }
                it[updatedAt] = now
            }
        } else {
            runCatching { create(email) }.getOrElse {
                val existing = PasswordResetVerifyAttemptTable
                    .selectAll()
                    .where { PasswordResetVerifyAttemptTable.emailLookup eq lookup(email) }
                    .forUpdate()
                    .firstOrNull()
                    ?: throw IllegalStateException("Password reset verify attempt row missing after concurrent create.")

                val windowExpired = now - existing[PasswordResetVerifyAttemptTable.lastAttemptAt] > ATTEMPT_WINDOW_MS
                val baseAttempts = if (windowExpired) 0 else existing[PasswordResetVerifyAttemptTable.attempts]
                val newAttempts = baseAttempts + 1
                PasswordResetVerifyAttemptTable.update({ PasswordResetVerifyAttemptTable.emailLookup eq lookup(email) }) {
                    it[this.attempts] = newAttempts
                    it[this.lastAttemptAt] = now
                    if (windowExpired) {
                        it[this.lockedUntil] = null
                    }
                    it[updatedAt] = now
                }
            }
        }

        return findByEmail(email)
            ?: throw IllegalStateException("Password reset verify attempt row missing after increment.")
    }

    override fun updateLockoutIfLater(email: String, lockUntil: Long): Boolean {
        val existing = PasswordResetVerifyAttemptTable
            .selectAll()
            .where { PasswordResetVerifyAttemptTable.emailLookup eq lookup(email) }
            .forUpdate()
            .firstOrNull()
            ?: return false

        val currentLockUntil = existing[PasswordResetVerifyAttemptTable.lockedUntil]
        if (currentLockUntil != null && currentLockUntil >= lockUntil) return false

        return PasswordResetVerifyAttemptTable.update({ PasswordResetVerifyAttemptTable.emailLookup eq lookup(email) }) {
            it[this.lockedUntil] = lockUntil
            it[updatedAt] = System.currentTimeMillis()
        } > 0
    }

    override fun delete(email: String): Boolean {
        return PasswordResetVerifyAttemptTable.deleteWhere { PasswordResetVerifyAttemptTable.emailLookup eq lookup(email) } > 0
    }

    override suspend fun deleteSafe(email: String): Boolean = dbQuery {
        delete(email)
    }

    private fun ResultRow.toDomain(): PasswordResetVerifyAttempt {
        return PasswordResetVerifyAttempt(
            id = this[PasswordResetVerifyAttemptTable.id],
            attempts = this[PasswordResetVerifyAttemptTable.attempts],
            lastAttemptAt = this[PasswordResetVerifyAttemptTable.lastAttemptAt],
            lockedUntil = this[PasswordResetVerifyAttemptTable.lockedUntil],
            createdAt = this[PasswordResetVerifyAttemptTable.createdAt],
            updatedAt = this[PasswordResetVerifyAttemptTable.updatedAt]
        )
    }

    private fun lookup(email: String): String = piiCrypto.emailLookup(email)
}
