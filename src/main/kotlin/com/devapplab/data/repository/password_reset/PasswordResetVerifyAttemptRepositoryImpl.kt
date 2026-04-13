package com.devapplab.data.repository.password_reset

import com.devapplab.data.database.password_reset.PasswordResetVerifyAttemptTable
import com.devapplab.model.password_reset.PasswordResetVerifyAttempt
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class PasswordResetVerifyAttemptRepositoryImpl : PasswordResetVerifyAttemptRepository {
    override fun findByEmail(email: String): PasswordResetVerifyAttempt? {
        return PasswordResetVerifyAttemptTable
            .selectAll()
            .where { PasswordResetVerifyAttemptTable.email eq email }
            .firstOrNull()
            ?.toDomain()
    }

    override fun create(email: String): PasswordResetVerifyAttempt {
        val now = System.currentTimeMillis()

        val row = PasswordResetVerifyAttemptTable.insert {
            it[this.email] = email
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
            .where { PasswordResetVerifyAttemptTable.email eq email }
            .forUpdate()
            .firstOrNull()

        if (lockedRow != null) {
            val newAttempts = lockedRow[PasswordResetVerifyAttemptTable.attempts] + 1
            PasswordResetVerifyAttemptTable.update({ PasswordResetVerifyAttemptTable.email eq email }) {
                it[this.attempts] = newAttempts
                it[this.lastAttemptAt] = now
                it[updatedAt] = now
            }
        } else {
            runCatching { create(email) }.getOrElse {
                val existing = PasswordResetVerifyAttemptTable
                    .selectAll()
                    .where { PasswordResetVerifyAttemptTable.email eq email }
                    .forUpdate()
                    .firstOrNull()
                    ?: throw IllegalStateException("Verify attempt row missing after concurrent create for email=$email")

                val newAttempts = existing[PasswordResetVerifyAttemptTable.attempts] + 1
                PasswordResetVerifyAttemptTable.update({ PasswordResetVerifyAttemptTable.email eq email }) {
                    it[this.attempts] = newAttempts
                    it[this.lastAttemptAt] = now
                    it[updatedAt] = now
                }
            }
        }

        return findByEmail(email)
            ?: throw IllegalStateException("Verify attempt row missing after increment for email=$email")
    }

    override fun updateLockoutIfLater(email: String, lockUntil: Long): Boolean {
        val existing = PasswordResetVerifyAttemptTable
            .selectAll()
            .where { PasswordResetVerifyAttemptTable.email eq email }
            .forUpdate()
            .firstOrNull()
            ?: return false

        val currentLockUntil = existing[PasswordResetVerifyAttemptTable.lockedUntil]
        if (currentLockUntil != null && currentLockUntil >= lockUntil) return false

        return PasswordResetVerifyAttemptTable.update({ PasswordResetVerifyAttemptTable.email eq email }) {
            it[this.lockedUntil] = lockUntil
            it[updatedAt] = System.currentTimeMillis()
        } > 0
    }

    override fun delete(email: String): Boolean {
        return PasswordResetVerifyAttemptTable.deleteWhere { PasswordResetVerifyAttemptTable.email eq email } > 0
    }

    private fun ResultRow.toDomain(): PasswordResetVerifyAttempt {
        return PasswordResetVerifyAttempt(
            id = this[PasswordResetVerifyAttemptTable.id],
            email = this[PasswordResetVerifyAttemptTable.email],
            attempts = this[PasswordResetVerifyAttemptTable.attempts],
            lastAttemptAt = this[PasswordResetVerifyAttemptTable.lastAttemptAt],
            lockedUntil = this[PasswordResetVerifyAttemptTable.lockedUntil],
            createdAt = this[PasswordResetVerifyAttemptTable.createdAt],
            updatedAt = this[PasswordResetVerifyAttemptTable.updatedAt]
        )
    }
}

