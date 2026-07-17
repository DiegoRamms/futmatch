package com.devapplab.data.repository

import com.devapplab.config.dbQuery
import com.devapplab.data.database.refresh_token.RefreshTokenTable
import com.devapplab.data.database.user.UserTable
import com.devapplab.model.auth.RefreshTokenRecord
import com.devapplab.model.auth.RefreshTokenStatus
import com.devapplab.model.auth.RefreshTokenStatusReason
import com.devapplab.model.auth.RefreshTokenValidationRecord
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import java.util.*

class RefreshTokenRepositoryImp : RefreshTokenRepository {

    override fun saveToken(userId: UUID, deviceId: UUID, token: String, expiresAt: Long): UUID {
        val result = RefreshTokenTable.insert {
            it[RefreshTokenTable.userId] = userId
            it[RefreshTokenTable.deviceId] = deviceId
            it[RefreshTokenTable.token] = token
            it[RefreshTokenTable.expiresAt] = expiresAt
            it[createdAt] = System.currentTimeMillis()
            it[status] = RefreshTokenStatus.ACTIVE.name
            it[statusReason] = RefreshTokenStatusReason.TOKEN_ISSUED.name
            it[revoked] = false
            it[revokedAt] = null
        }
        return result[RefreshTokenTable.id]
    }

    override fun findByTokenHash(tokenHash: String): RefreshTokenRecord? {
        return RefreshTokenTable.select(RefreshTokenTable.columns)
            .where { RefreshTokenTable.token eq tokenHash }
            .orderBy(RefreshTokenTable.createdAt, SortOrder.DESC)
            .limit(1)
            .mapNotNull { it.toRefreshTokenRecord() }
            .singleOrNull()
    }

    override fun findValidationByTokenHash(tokenHash: String): RefreshTokenValidationRecord? {
        return (RefreshTokenTable innerJoin UserTable)
            .select(
                listOf(
                    RefreshTokenTable.id,
                    RefreshTokenTable.userId,
                    RefreshTokenTable.deviceId,
                    RefreshTokenTable.expiresAt,
                    RefreshTokenTable.createdAt,
                    RefreshTokenTable.status,
                    RefreshTokenTable.statusReason,
                    UserTable.role
                )
            )
            .where { RefreshTokenTable.token eq tokenHash }
            .limit(1)
            .singleOrNull()
            ?.let { row ->
                RefreshTokenValidationRecord(
                    id = row[RefreshTokenTable.id],
                    userId = row[RefreshTokenTable.userId],
                    deviceId = row[RefreshTokenTable.deviceId],
                    expiresAt = row[RefreshTokenTable.expiresAt],
                    createdAt = row[RefreshTokenTable.createdAt],
                    status = RefreshTokenStatus.valueOf(row[RefreshTokenTable.status]),
                    statusReason = row[RefreshTokenTable.statusReason]?.let(RefreshTokenStatusReason::valueOf),
                    userRole = row[UserTable.role]
                )
            }
    }

    override fun markTokenAsRotatedIfActive(tokenId: UUID, changedAt: Long): Boolean {
        return RefreshTokenTable.update({
            (RefreshTokenTable.id eq tokenId) and
                (RefreshTokenTable.status eq RefreshTokenStatus.ACTIVE.name)
        }) {
            it[revoked] = true
            it[status] = RefreshTokenStatus.ROTATED.name
            it[statusReason] = RefreshTokenStatusReason.TOKEN_ROTATED.name
            it[revokedAt] = changedAt
        } == 1
    }

    override fun markPreviousActiveTokensAsRotated(deviceId: UUID, currentTokenId: UUID, changedAt: Long): Boolean {
        return RefreshTokenTable.update({
            (RefreshTokenTable.deviceId eq deviceId) and
                (RefreshTokenTable.id neq currentTokenId) and
                (RefreshTokenTable.status eq RefreshTokenStatus.ACTIVE.name)
        }) {
            it[revoked] = true
            it[status] = RefreshTokenStatus.ROTATED.name
            it[statusReason] = RefreshTokenStatusReason.TOKEN_ROTATED.name
            it[revokedAt] = changedAt
        } > 0
    }

    override fun updateTokenStatus(
        tokenId: UUID,
        status: RefreshTokenStatus,
        reason: RefreshTokenStatusReason,
        changedAt: Long
    ): Boolean {
        return RefreshTokenTable.update({ RefreshTokenTable.id eq tokenId }) {
            it[revoked] = status != RefreshTokenStatus.ACTIVE
            it[RefreshTokenTable.status] = status.name
            it[statusReason] = reason.name
            it[revokedAt] = changedAt
        } > 0
    }

    override fun revokeActiveTokens(deviceId: UUID, reason: RefreshTokenStatusReason, changedAt: Long): Boolean {
        return RefreshTokenTable.update({
            (RefreshTokenTable.deviceId eq deviceId) and (RefreshTokenTable.status eq RefreshTokenStatus.ACTIVE.name)
        }) {
            it[revoked] = true
            it[status] = RefreshTokenStatus.REVOKED.name
            it[statusReason] = reason.name
            it[revokedAt] = changedAt
        } > 0
    }

    override fun revokeActiveTokensByUserId(userId: UUID, reason: RefreshTokenStatusReason, changedAt: Long): Int {
        return RefreshTokenTable.update({
            (RefreshTokenTable.userId eq userId) and (RefreshTokenTable.status eq RefreshTokenStatus.ACTIVE.name)
        }) {
            it[revoked] = true
            it[status] = RefreshTokenStatus.REVOKED.name
            it[statusReason] = reason.name
            it[revokedAt] = changedAt
        }
    }

    override suspend fun deleteRevokedTokens(): Boolean = dbQuery {
        val now = System.currentTimeMillis()
        RefreshTokenTable.deleteWhere {
            (RefreshTokenTable.status neq RefreshTokenStatus.ACTIVE.name) or (expiresAt less now)
        } > 0
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
            revoked = this[RefreshTokenTable.revoked],
            status = RefreshTokenStatus.valueOf(this[RefreshTokenTable.status]),
            statusReason = this[RefreshTokenTable.statusReason]?.let(RefreshTokenStatusReason::valueOf),
            revokedAt = this[RefreshTokenTable.revokedAt]
        )

}
