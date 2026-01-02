package com.devapplab.data.database.password_reset

import com.devapplab.config.dbQuery
import com.devapplab.model.password_reset.PasswordResetTokenRecord
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.*

interface PasswordResetTokenDao {
    suspend fun create(token: String, userId: UUID, expiresAt: Long): PasswordResetTokenRecord?
    suspend fun findByToken(token: String): PasswordResetTokenRecord?
    suspend fun delete(token: String)
    suspend fun deleteByUserId(userId: UUID)
}

class PasswordResetTokenDaoImpl : PasswordResetTokenDao {
    override suspend fun create(token: String, userId: UUID, expiresAt: Long): PasswordResetTokenRecord? {
        return dbQuery {
            PasswordResetTokensTable.insert {
                it[PasswordResetTokensTable.token] = token
                it[PasswordResetTokensTable.userId] = userId
                it[PasswordResetTokensTable.expiresAt] = expiresAt
            }.resultedValues?.singleOrNull()?.let(::toPasswordResetTokenRecord)
        }
    }

    override suspend fun findByToken(token: String): PasswordResetTokenRecord? {
        return dbQuery {
            PasswordResetTokensTable.selectAll().where { PasswordResetTokensTable.token eq token }
                .singleOrNull()
                ?.let(::toPasswordResetTokenRecord)
        }
    }

    override suspend fun delete(token: String) {
        dbQuery {
            PasswordResetTokensTable.deleteWhere { PasswordResetTokensTable.token eq token }
        }
    }

    override suspend fun deleteByUserId(userId: UUID) {
        dbQuery {
            PasswordResetTokensTable.deleteWhere { PasswordResetTokensTable.userId eq userId }
        }
    }

    private fun toPasswordResetTokenRecord(row: ResultRow): PasswordResetTokenRecord {
        return PasswordResetTokenRecord(
            token = row[PasswordResetTokensTable.token],
            userId = row[PasswordResetTokensTable.userId],
            expiresAt = row[PasswordResetTokensTable.expiresAt]
        )
    }
}
