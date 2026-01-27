package com.devapplab.data.repository

import com.devapplab.config.dbQuery
import com.devapplab.data.database.refresh_token.RefreshTokenTable
import com.devapplab.data.database.user.UserTable
import com.devapplab.model.auth.RefreshTokenRecord
import com.devapplab.model.auth.RefreshTokenValidationInfo
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.*

class RefreshTokenRepositoryImp : RefreshTokenRepository {

    override fun saveToken(userId: UUID, deviceId: UUID, token: String, expiresAt: Long): UUID {
        val result = RefreshTokenTable.insert {
            it[RefreshTokenTable.userId] = userId
            it[RefreshTokenTable.deviceId] = deviceId
            it[RefreshTokenTable.token] = token
            it[RefreshTokenTable.expiresAt] = expiresAt
            it[createdAt] = System.currentTimeMillis()
        }
        return result[RefreshTokenTable.id]
    }


    override suspend fun findLatestTokenByUserId(userId: UUID): RefreshTokenRecord? = dbQuery {
        RefreshTokenTable.selectAll()
            .where { (RefreshTokenTable.userId eq userId) and (RefreshTokenTable.revoked eq false) }
            .orderBy(RefreshTokenTable.createdAt, SortOrder.DESC)
            .limit(1)
            .mapNotNull { it.toRefreshTokenRecord() }
            .singleOrNull()
    }

    override fun getValidationInfo(deviceId: UUID): RefreshTokenValidationInfo? {
        return (RefreshTokenTable innerJoin UserTable)
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
            .mapNotNull { it.toRefreshTokenValidationInfo() }
            .singleOrNull()
    }

    override fun revokeToken(deviceId: UUID): Boolean {
        val lastTokenId = RefreshTokenTable
            .selectAll()
            .where { (RefreshTokenTable.deviceId eq deviceId) and (RefreshTokenTable.revoked eq false) }
            .orderBy(RefreshTokenTable.createdAt, SortOrder.DESC)
            .limit(1)
            .map { it[RefreshTokenTable.id] }
            .singleOrNull() ?: return false

        return RefreshTokenTable.update({
            (RefreshTokenTable.deviceId eq deviceId) and
                    (RefreshTokenTable.id neq lastTokenId) and
                    (RefreshTokenTable.revoked eq false)
        }) {
            it[revoked] = true
        } > 0
    }

    override fun revokeCurrentToken(deviceId: UUID): Boolean {
        return RefreshTokenTable.update({
            (RefreshTokenTable.deviceId eq deviceId) and (RefreshTokenTable.revoked eq false)
        }) {
            it[revoked] = true
        } > 0
    }

    override suspend fun deleteRevokedTokens(): Boolean = dbQuery {
        RefreshTokenTable.deleteWhere { revoked eq true } > 0
    }

    private fun ResultRow.toRefreshTokenRecord(): RefreshTokenRecord =
        RefreshTokenRecord(
            id = this[RefreshTokenTable.id],
            userId = this[RefreshTokenTable.userId],
            deviceId = this[RefreshTokenTable.deviceId],
            token = this[RefreshTokenTable.token],
            expiresAt = this[RefreshTokenTable.expiresAt],
            createdAt = this[RefreshTokenTable.createdAt],
            ipAddress = this[RefreshTokenTable.ipAddress],
            userAgent = this[RefreshTokenTable.userAgent],
            revoked = this[RefreshTokenTable.revoked]
        )

    private fun ResultRow.toRefreshTokenValidationInfo(): RefreshTokenValidationInfo =
        RefreshTokenValidationInfo(
            userId = this[RefreshTokenTable.userId],
            isEmailVerified = this[UserTable.isEmailVerified],
            token = this[RefreshTokenTable.token],
            expiresAt = this[RefreshTokenTable.expiresAt],
            createdAt = this[RefreshTokenTable.createdAt],
            revoked = this[RefreshTokenTable.revoked]
        )
}