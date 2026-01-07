package com.devapplab.data.repository.password_reset

import com.devapplab.data.database.password_reset.PasswordResetTokenDao
import com.devapplab.model.password_reset.PasswordResetTokenRecord
import java.util.UUID

interface PasswordResetTokenRepository {
    fun create(token: String, userId: UUID, expiresAt: Long): PasswordResetTokenRecord?
    fun findByToken(token: String): PasswordResetTokenRecord?
    fun delete(token: String)
    fun deleteByUserId(userId: UUID)
}

class PasswordResetTokenRepositoryImpl(private val dao: PasswordResetTokenDao) : PasswordResetTokenRepository {
    override fun create(token: String, userId: UUID, expiresAt: Long): PasswordResetTokenRecord? {
        return dao.create(token, userId, expiresAt)
    }

    override fun findByToken(token: String): PasswordResetTokenRecord? {
        return dao.findByToken(token)
    }

    override fun delete(token: String) {
        dao.delete(token)
    }

    override fun deleteByUserId(userId: UUID) {
        dao.deleteByUserId(userId)
    }
}
