package com.devapplab.data.database.pending_registrations

import com.devapplab.config.dbQuery
import com.devapplab.model.auth.request.RegisterUserRequest
import com.devapplab.model.pending_registration.PendingRegistration
import model.user.UserRole
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import java.util.UUID

interface PendingRegistrationDao {
    suspend fun create(request: RegisterUserRequest, verificationCode: String, expiresAt: Long): PendingRegistration?
    suspend fun findByEmail(email: String): PendingRegistration?
    suspend fun updateCodeAndExpiration(id: UUID, newCode: String, newExpiresAt: Long, newUpdatedAt: Long): Boolean
    suspend fun delete(id: UUID): Boolean
    suspend fun deleteExpired(timestamp: Long): Int
}

class PendingRegistrationDaoImpl : PendingRegistrationDao {

    override suspend fun create(request: RegisterUserRequest, verificationCode: String, expiresAt: Long): PendingRegistration? = dbQuery {
        PendingRegistrationTable.insert {
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
            it[userRole] = UserRole.PLAYER
            it[PendingRegistrationTable.verificationCode] = verificationCode
            it[PendingRegistrationTable.expiresAt] = expiresAt
            it[createdAt] = System.currentTimeMillis()
            it[updatedAt] = System.currentTimeMillis()
        }.resultedValues?.singleOrNull()?.let(::toPendingRegistration)
    }

    override suspend fun findByEmail(email: String): PendingRegistration? = dbQuery {
        PendingRegistrationTable.selectAll().where { PendingRegistrationTable.email eq email }
            .singleOrNull()
            ?.let(::toPendingRegistration)
    }

    override suspend fun updateCodeAndExpiration(id: UUID, newCode: String, newExpiresAt: Long, newUpdatedAt: Long): Boolean = dbQuery {
        PendingRegistrationTable.update({ PendingRegistrationTable.id eq id }) {
            it[verificationCode] = newCode
            it[expiresAt] = newExpiresAt
            it[updatedAt] = newUpdatedAt
        } > 0
    }

    override suspend fun delete(id: UUID): Boolean = dbQuery {
        PendingRegistrationTable.deleteWhere { PendingRegistrationTable.id eq id } > 0
    }

    override suspend fun deleteExpired(timestamp: Long): Int = dbQuery {
        PendingRegistrationTable.deleteWhere { expiresAt less timestamp }
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
