package com.devapplab.data.repository.pending_registrations

import com.devapplab.data.database.pending_registrations.PendingRegistrationDao
import com.devapplab.model.auth.request.RegisterUserRequest
import com.devapplab.model.pending_registration.PendingRegistration
import java.util.UUID

interface PendingRegistrationRepository {
    suspend fun create(request: RegisterUserRequest, verificationCode: String, expiresAt: Long): PendingRegistration?
    suspend fun findByEmail(email: String): PendingRegistration?
    suspend fun updateCodeAndExpiration(id: UUID, newCode: String, newExpiresAt: Long, newUpdatedAt: Long): Boolean
    suspend fun delete(id: UUID): Boolean
    suspend fun deleteExpired(timestamp: Long): Int
}

class PendingRegistrationRepositoryImpl(private val dao: PendingRegistrationDao) : PendingRegistrationRepository {

    override suspend fun create(request: RegisterUserRequest, verificationCode: String, expiresAt: Long): PendingRegistration? {
        return dao.create(request, verificationCode, expiresAt)
    }

    override suspend fun findByEmail(email: String): PendingRegistration? {
        return dao.findByEmail(email)
    }

    override suspend fun updateCodeAndExpiration(id: UUID, newCode: String, newExpiresAt: Long, newUpdatedAt: Long): Boolean {
        return dao.updateCodeAndExpiration(id, newCode, newExpiresAt, newUpdatedAt)
    }

    override suspend fun delete(id: UUID): Boolean {
        return dao.delete(id)
    }

    override suspend fun deleteExpired(timestamp: Long): Int {
        return dao.deleteExpired(timestamp)
    }
}

