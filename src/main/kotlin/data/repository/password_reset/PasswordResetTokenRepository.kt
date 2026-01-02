package com.devapplab.data.repository.password_reset

import com.devapplab.data.database.password_reset.PasswordResetTokenDao
import com.devapplab.model.password_reset.PasswordResetTokenRecord
import java.util.UUID

interface PasswordResetTokenRepository {
    suspend fun create(token: String, userId: UUID, expiresAt: Long): PasswordResetTokenRecord?
    suspend fun findByToken(token: String): PasswordResetTokenRecord?
    suspend fun delete(token: String)
    suspend fun deleteByUserId(userId: UUID)
}

class PasswordResetTokenRepositoryImpl(private val dao: PasswordResetTokenDao) : PasswordResetTokenRepository {
    override suspend fun create(token: String, userId: UUID, expiresAt: Long): PasswordResetTokenRecord? {
        return dao.create(token, userId, expiresAt)
    }

    override suspend fun findByToken(token: String): PasswordResetTokenRecord? {
        return dao.findByToken(token)
    }

    override suspend fun delete(token: String) {
        dao.delete(token)
    }

    override suspend fun deleteByUserId(userId: UUID) {
        dao.deleteByUserId(userId)
    }
}
