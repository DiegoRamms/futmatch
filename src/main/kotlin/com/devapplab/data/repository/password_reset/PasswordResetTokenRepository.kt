package com.devapplab.data.repository.password_reset

import com.devapplab.data.database.password_reset.PasswordResetTokensTable
import com.devapplab.model.password_reset.PasswordResetTokenRecord
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.util.*

interface PasswordResetTokenRepository {
    fun create(token: String, userId: UUID, expiresAt: Long): PasswordResetTokenRecord?
    fun findByToken(token: String): PasswordResetTokenRecord?
    fun delete(token: String)
    fun deleteByUserId(userId: UUID)
}

class PasswordResetTokenRepositoryImpl : PasswordResetTokenRepository {
    override fun create(token: String, userId: UUID, expiresAt: Long): PasswordResetTokenRecord? {
        return PasswordResetTokensTable.insert {
            it[PasswordResetTokensTable.token] = token
            it[PasswordResetTokensTable.userId] = userId
            it[PasswordResetTokensTable.expiresAt] = expiresAt
        }.resultedValues?.singleOrNull()?.let(::toPasswordResetTokenRecord)
    }

    override fun findByToken(token: String): PasswordResetTokenRecord? {
        return PasswordResetTokensTable.selectAll().where { PasswordResetTokensTable.token eq token }
            .singleOrNull()
            ?.let(::toPasswordResetTokenRecord)
    }

    override fun delete(token: String) {
        PasswordResetTokensTable.deleteWhere { PasswordResetTokensTable.token eq token }
    }

    override fun deleteByUserId(userId: UUID) {
        PasswordResetTokensTable.deleteWhere { PasswordResetTokensTable.userId eq userId }
    }

    private fun toPasswordResetTokenRecord(row: ResultRow): PasswordResetTokenRecord {
        return PasswordResetTokenRecord(
            token = row[PasswordResetTokensTable.token],
            userId = row[PasswordResetTokensTable.userId],
            expiresAt = row[PasswordResetTokensTable.expiresAt]
        )
    }
}
