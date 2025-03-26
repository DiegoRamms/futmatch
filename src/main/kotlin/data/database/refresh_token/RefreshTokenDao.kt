package com.devapplab.data.database.refresh_token

import com.devapplab.config.dbQuery
import com.devapplab.data.database.user.UserTable
import model.auth.RefreshTokenRecord
import model.auth.RefreshTokenValidationInfo
import org.jetbrains.exposed.sql.*
import java.util.*

class RefreshTokenDao {
    suspend fun saveToken(userId: UUID, deviceId: UUID, token: String, expiresAt: Long): Boolean = dbQuery {
        RefreshTokenTable.insert {
            it[id] = UUID.randomUUID()
            it[RefreshTokenTable.userId] = userId
            it[RefreshTokenTable.deviceId] = deviceId
            it[RefreshTokenTable.token] = token
            it[RefreshTokenTable.expiresAt] = expiresAt
            it[createdAt] = System.currentTimeMillis()
        }.insertedCount > 0
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
        val latestCreatedAt = RefreshTokenTable
            .select(RefreshTokenTable.createdAt)
            .where { RefreshTokenTable.deviceId eq deviceId and (RefreshTokenTable.revoked eq false) }
            .orderBy(RefreshTokenTable.createdAt, SortOrder.DESC)
            .limit(1)
            .mapNotNull { it[RefreshTokenTable.createdAt] }
            .singleOrNull() ?: return@dbQuery false

        RefreshTokenTable.update({
            (RefreshTokenTable.deviceId eq deviceId) and
                    (RefreshTokenTable.createdAt less latestCreatedAt) and
                    (RefreshTokenTable.revoked eq false)
        }) {
            it[revoked] = true
        } > 0
    }

}