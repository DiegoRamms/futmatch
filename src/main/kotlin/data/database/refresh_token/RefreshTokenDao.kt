package com.devapplab.data.database.refresh_token

import com.devapplab.config.dbQuery
import com.devapplab.data.database.user.UserTable
import model.auth.RefreshTokenRecord
import model.auth.RefreshTokenValidationInfo
import org.jetbrains.exposed.sql.*
import java.util.*

class RefreshTokenDao {
    fun saveToken(userId: UUID, deviceId: UUID, token: String, expiresAt: Long): UUID {
        val result = RefreshTokenTable.insert {
            it[RefreshTokenTable.userId] = userId
            it[RefreshTokenTable.deviceId] = deviceId
            it[RefreshTokenTable.token] = token
            it[RefreshTokenTable.expiresAt] = expiresAt
            it[createdAt] = System.currentTimeMillis()
        }
        return result[RefreshTokenTable.id]
    }

    suspend fun findByTokenByUserId(userId: UUID): RefreshTokenRecord? = dbQuery {
        RefreshTokenTable.selectAll()
            .orderBy(RefreshTokenTable.createdAt to SortOrder.DESC)
            .limit(1)
            .where { (RefreshTokenTable.userId eq userId) and (RefreshTokenTable.revoked eq false) }
            .mapNotNull { row ->
                RefreshTokenRecord(
                    id = row[RefreshTokenTable.id],
                    userId = row[RefreshTokenTable.userId],
                    deviceId = row[RefreshTokenTable.deviceId],
                    token = row[RefreshTokenTable.token],
                    expiresAt = row[RefreshTokenTable.expiresAt],
                    createdAt = row[RefreshTokenTable.createdAt],
                    ipAddress = row[RefreshTokenTable.ipAddress],
                    userAgent = row[RefreshTokenTable.userAgent],
                    revoked = row[RefreshTokenTable.revoked]
                )
            }
            .singleOrNull()
    }

    suspend fun getRefreshTokenValidationInfo(deviceId: UUID): RefreshTokenValidationInfo? = dbQuery {
        (RefreshTokenTable innerJoin UserTable)
            .select(
                RefreshTokenTable.userId,
                RefreshTokenTable.token,
                RefreshTokenTable.expiresAt,
                RefreshTokenTable.createdAt,
                RefreshTokenTable.revoked,
                UserTable.isEmailVerified
            )
            .where { (RefreshTokenTable.deviceId eq deviceId) and (RefreshTokenTable.revoked eq false) }
            .orderBy(RefreshTokenTable.createdAt, SortOrder.DESC)
            .limit(1)
            .mapNotNull { row ->
                RefreshTokenValidationInfo(
                    userId = row[RefreshTokenTable.userId],
                    isEmailVerified = row[UserTable.isEmailVerified],
                    token = row[RefreshTokenTable.token],
                    expiresAt = row[RefreshTokenTable.expiresAt],
                    createdAt = row[RefreshTokenTable.createdAt],
                    revoked = row[RefreshTokenTable.revoked]
                )
            }
            .singleOrNull()
    }

    suspend fun revokeToken(deviceId: UUID): Boolean = dbQuery {
        val lastTokenId = RefreshTokenTable
            .select(RefreshTokenTable.id)
            .where { RefreshTokenTable.deviceId eq deviceId and (RefreshTokenTable.revoked eq false) }
            .orderBy(RefreshTokenTable.createdAt, SortOrder.DESC)
            .limit(1)
            .map { it[RefreshTokenTable.id] }
            .singleOrNull() ?: return@dbQuery false


        val updated = RefreshTokenTable.update({
            (RefreshTokenTable.deviceId eq deviceId) and
                    (RefreshTokenTable.id neq lastTokenId) and
                    (RefreshTokenTable.revoked eq false)
        }) {
            it[revoked] = true
        }

        return@dbQuery updated > 0
    }
}