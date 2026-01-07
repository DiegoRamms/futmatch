package com.devapplab.data.repository.pending_registrations

import com.devapplab.data.database.pending_registrations.PendingRegistrationDao
import com.devapplab.model.auth.request.RegisterUserRequest
import com.devapplab.model.pending_registration.PendingRegistration
import java.util.UUID

interface PendingRegistrationRepository {
    fun create(request: RegisterUserRequest, hashedVerificationCode: String, expiresAt: Long): PendingRegistration?
    fun findByEmail(email: String): PendingRegistration?
    fun updateCodeAndExpiration(id: UUID, newCode: String, newExpiresAt: Long, newUpdatedAt: Long): Boolean
    fun delete(id: UUID): Boolean
    fun deleteExpired(timestamp: Long): Int
}

class PendingRegistrationRepositoryImpl(private val dao: PendingRegistrationDao) : PendingRegistrationRepository {

    override fun create(request: RegisterUserRequest, hashedVerificationCode: String, expiresAt: Long): PendingRegistration? {
        return dao.create(request, hashedVerificationCode, expiresAt)
    }

    override fun findByEmail(email: String): PendingRegistration? {
        return dao.findByEmail(email)
    }

    override fun updateCodeAndExpiration(id: UUID, newCode: String, newExpiresAt: Long, newUpdatedAt: Long): Boolean {
        return dao.updateCodeAndExpiration(id, newCode, newExpiresAt, newUpdatedAt)
    }

    override fun delete(id: UUID): Boolean {
        return dao.delete(id)
    }

    override fun deleteExpired(timestamp: Long): Int {
        return dao.deleteExpired(timestamp)
    }
}

