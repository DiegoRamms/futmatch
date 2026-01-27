package com.devapplab.data.repository.pending_registrations

import com.devapplab.data.database.pending_registrations.PendingRegistrationTable
import com.devapplab.model.auth.request.RegisterUserRequest
import com.devapplab.model.pending_registration.PendingRegistration
import com.devapplab.model.user.UserRole
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import java.util.*

interface PendingRegistrationRepository {
    fun create(request: RegisterUserRequest, hashedVerificationCode: String, expiresAt: Long): PendingRegistration?
    fun findByEmail(email: String): PendingRegistration?
    fun updateCodeAndExpiration(id: UUID, newCode: String, newExpiresAt: Long, newUpdatedAt: Long): Boolean
    fun delete(id: UUID): Boolean
    fun deleteExpired(timestamp: Long): Int
}

class PendingRegistrationRepositoryImpl : PendingRegistrationRepository {

    override fun create(request: RegisterUserRequest, hashedVerificationCode: String, expiresAt: Long): PendingRegistration? {
        return PendingRegistrationTable.insert {
            it[name] = request.name
            it[lastName] = request.lastName
            it[email] = request.email
            it[password] = request.password
            it[phone] = request.phone
            it[country] = request.country
            it[birthDate] = request.birthDate
            it[playerPosition] = request.playerPosition
            it[gender] = request.gender
            it[profilePic] = request.profilePic
            it[level] = request.level
            it[userRole] = UserRole.BOTH //TODO remove this line ,the deault should be UserRole.PLAYER this is just for development
            it[verificationCode] = hashedVerificationCode
            it[PendingRegistrationTable.expiresAt] = expiresAt
            it[createdAt] = System.currentTimeMillis()
            it[updatedAt] = System.currentTimeMillis()
        }.resultedValues?.singleOrNull()?.let(::toPendingRegistration)
    }

    override fun findByEmail(email: String): PendingRegistration? {
        return PendingRegistrationTable.selectAll().where { PendingRegistrationTable.email eq email }
            .singleOrNull()
            ?.let(::toPendingRegistration)
    }

    override fun updateCodeAndExpiration(id: UUID, newCode: String, newExpiresAt: Long, newUpdatedAt: Long): Boolean {
        return PendingRegistrationTable.update({ PendingRegistrationTable.id eq id }) {
            it[verificationCode] = newCode
            it[expiresAt] = newExpiresAt
            it[updatedAt] = newUpdatedAt
        } > 0
    }

    override fun delete(id: UUID): Boolean {
        return PendingRegistrationTable.deleteWhere { PendingRegistrationTable.id eq id } > 0
    }

    override fun deleteExpired(timestamp: Long): Int {
        return PendingRegistrationTable.deleteWhere { expiresAt less timestamp }
    }

    private fun toPendingRegistration(row: ResultRow): PendingRegistration {
        return PendingRegistration(
            id = row[PendingRegistrationTable.id],
            name = row[PendingRegistrationTable.name],
            lastName = row[PendingRegistrationTable.lastName],
            email = row[PendingRegistrationTable.email],
            password = row[PendingRegistrationTable.password],
            phone = row[PendingRegistrationTable.phone],
            country = row[PendingRegistrationTable.country],
            birthDate = row[PendingRegistrationTable.birthDate],
            playerPosition = row[PendingRegistrationTable.playerPosition],
            gender = row[PendingRegistrationTable.gender],
            profilePic = row[PendingRegistrationTable.profilePic],
            level = row[PendingRegistrationTable.level],
            userRole = row[PendingRegistrationTable.userRole],
            verificationCode = row[PendingRegistrationTable.verificationCode],
            expiresAt = row[PendingRegistrationTable.expiresAt],
            createdAt = row[PendingRegistrationTable.createdAt],
            updatedAt = row[PendingRegistrationTable.updatedAt]
        )
    }
}

