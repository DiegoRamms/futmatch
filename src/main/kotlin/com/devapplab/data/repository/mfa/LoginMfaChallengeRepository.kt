package com.devapplab.data.repository.mfa

import com.devapplab.data.database.mfa.LoginMfaChallengeTable
import com.devapplab.config.dbQuery
import com.devapplab.model.mfa.LoginMfaChallenge
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import java.util.UUID

interface LoginMfaChallengeRepository {
    fun create(tokenHash: String, userId: UUID, deviceId: UUID, expiresAt: Long, createdAt: Long): LoginMfaChallenge?
    fun findValidByTokenHash(tokenHash: String, now: Long): LoginMfaChallenge?
    fun revokeActiveByUserAndDevice(userId: UUID, deviceId: UUID, revokedAt: Long): Boolean
    fun markUsed(tokenHash: String, usedAt: Long): Boolean
    suspend fun deleteInactive(now: Long): Boolean
}

class LoginMfaChallengeRepositoryImpl : LoginMfaChallengeRepository {
    override fun create(
        tokenHash: String,
        userId: UUID,
        deviceId: UUID,
        expiresAt: Long,
        createdAt: Long
    ): LoginMfaChallenge? {
        return LoginMfaChallengeTable.insert {
            it[LoginMfaChallengeTable.tokenHash] = tokenHash
            it[LoginMfaChallengeTable.userId] = userId
            it[LoginMfaChallengeTable.deviceId] = deviceId
            it[LoginMfaChallengeTable.expiresAt] = expiresAt
            it[LoginMfaChallengeTable.createdAt] = createdAt
        }.resultedValues?.singleOrNull()?.let(::toLoginMfaChallenge)
    }

    override fun findValidByTokenHash(tokenHash: String, now: Long): LoginMfaChallenge? {
        return LoginMfaChallengeTable.selectAll().where {
            (LoginMfaChallengeTable.tokenHash eq tokenHash) and
                (LoginMfaChallengeTable.usedAt.isNull()) and
                (LoginMfaChallengeTable.revokedAt.isNull()) and
                (LoginMfaChallengeTable.expiresAt greaterEq now)
        }.singleOrNull()?.let(::toLoginMfaChallenge)
    }

    override fun revokeActiveByUserAndDevice(userId: UUID, deviceId: UUID, revokedAt: Long): Boolean {
        return LoginMfaChallengeTable.update({
            (LoginMfaChallengeTable.userId eq userId) and
                (LoginMfaChallengeTable.deviceId eq deviceId) and
                (LoginMfaChallengeTable.usedAt.isNull()) and
                (LoginMfaChallengeTable.revokedAt.isNull())
        }) {
            it[LoginMfaChallengeTable.revokedAt] = revokedAt
        } > 0
    }

    override fun markUsed(tokenHash: String, usedAt: Long): Boolean {
        return LoginMfaChallengeTable.update({
            (LoginMfaChallengeTable.tokenHash eq tokenHash) and
                (LoginMfaChallengeTable.usedAt.isNull()) and
                (LoginMfaChallengeTable.revokedAt.isNull())
        }) {
            it[LoginMfaChallengeTable.usedAt] = usedAt
        } > 0
    }

    override suspend fun deleteInactive(now: Long): Boolean = dbQuery {
        LoginMfaChallengeTable.deleteWhere {
            (LoginMfaChallengeTable.expiresAt less now) or
                LoginMfaChallengeTable.usedAt.isNotNull() or
                LoginMfaChallengeTable.revokedAt.isNotNull()
        } > 0
    }

    private fun toLoginMfaChallenge(row: ResultRow): LoginMfaChallenge {
        return LoginMfaChallenge(
            tokenHash = row[LoginMfaChallengeTable.tokenHash],
            userId = row[LoginMfaChallengeTable.userId],
            deviceId = row[LoginMfaChallengeTable.deviceId],
            expiresAt = row[LoginMfaChallengeTable.expiresAt],
            createdAt = row[LoginMfaChallengeTable.createdAt],
            usedAt = row[LoginMfaChallengeTable.usedAt],
            revokedAt = row[LoginMfaChallengeTable.revokedAt]
        )
    }
}
