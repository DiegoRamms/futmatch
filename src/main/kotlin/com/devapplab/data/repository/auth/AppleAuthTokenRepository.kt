package com.devapplab.data.repository.auth

import com.devapplab.data.database.auth.AppleAuthTokenTable
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

data class AppleAuthToken(
    val userId: UUID,
    val refreshTokenCiphertext: String,
    val piiKeyVersion: String
)

interface AppleAuthTokenRepository {
    fun upsertTx(userId: UUID, refreshTokenCiphertext: String, piiKeyVersion: String)
    fun findByUserIdTx(userId: UUID): AppleAuthToken?
    fun deleteByUserIdTx(userId: UUID): Int
}

class AppleAuthTokenRepositoryImpl : AppleAuthTokenRepository {
    override fun upsertTx(userId: UUID, refreshTokenCiphertext: String, piiKeyVersion: String) {
        val now = System.currentTimeMillis()
        val updated = AppleAuthTokenTable.update({ AppleAuthTokenTable.userId eq userId }) {
            it[AppleAuthTokenTable.refreshTokenCiphertext] = refreshTokenCiphertext
            it[AppleAuthTokenTable.piiKeyVersion] = piiKeyVersion
            it[updatedAt] = now
        }
        if (updated == 0) {
            AppleAuthTokenTable.insert {
                it[AppleAuthTokenTable.userId] = userId
                it[AppleAuthTokenTable.refreshTokenCiphertext] = refreshTokenCiphertext
                it[AppleAuthTokenTable.piiKeyVersion] = piiKeyVersion
                it[createdAt] = now
                it[updatedAt] = now
            }
        }
    }

    override fun findByUserIdTx(userId: UUID): AppleAuthToken? =
        AppleAuthTokenTable.selectAll()
            .where { AppleAuthTokenTable.userId eq userId }
            .singleOrNull()
            ?.toAppleAuthToken()

    override fun deleteByUserIdTx(userId: UUID): Int =
        AppleAuthTokenTable.deleteWhere { AppleAuthTokenTable.userId eq userId }
}

private fun ResultRow.toAppleAuthToken(): AppleAuthToken = AppleAuthToken(
    userId = this[AppleAuthTokenTable.userId],
    refreshTokenCiphertext = this[AppleAuthTokenTable.refreshTokenCiphertext],
    piiKeyVersion = this[AppleAuthTokenTable.piiKeyVersion]
)
