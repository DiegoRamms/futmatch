package com.devapplab.data.repository.auth

import com.devapplab.data.database.auth.SocialLinkAttemptTable
import com.devapplab.model.auth.SocialLinkAttempt
import com.devapplab.model.auth.identity.AuthProvider
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

class SocialLinkAttemptRepositoryImpl : SocialLinkAttemptRepository {
    override fun createTx(tokenHash: String, userId: UUID, provider: AuthProvider, issuer: String, subject: String, expiresAt: Long, now: Long): SocialLinkAttempt =
        SocialLinkAttemptTable.insert {
            it[SocialLinkAttemptTable.tokenHash] = tokenHash
            it[SocialLinkAttemptTable.userId] = userId
            it[SocialLinkAttemptTable.provider] = provider
            it[SocialLinkAttemptTable.issuer] = issuer
            it[SocialLinkAttemptTable.providerSubject] = subject
            it[SocialLinkAttemptTable.expiresAt] = expiresAt
            it[createdAt] = now
        }.resultedValues!!.single().toAttempt()

    override fun findValidByTokenHashTx(tokenHash: String, now: Long): SocialLinkAttempt? = SocialLinkAttemptTable.selectAll().where {
        (SocialLinkAttemptTable.tokenHash eq tokenHash) and
            SocialLinkAttemptTable.usedAt.isNull() and
            (SocialLinkAttemptTable.expiresAt greaterEq now)
    }.singleOrNull()?.toAttempt()

    override fun setMfaCodeTx(id: UUID, mfaCodeId: UUID, passwordVerifiedAt: Long): Boolean =
        SocialLinkAttemptTable.update({ SocialLinkAttemptTable.id eq id }) {
            it[SocialLinkAttemptTable.mfaCodeId] = mfaCodeId
            it[SocialLinkAttemptTable.passwordVerifiedAt] = passwordVerifiedAt
        } > 0

    override fun consumeTx(id: UUID, now: Long): Boolean =
        SocialLinkAttemptTable.update({ (SocialLinkAttemptTable.id eq id) and SocialLinkAttemptTable.usedAt.isNull() }) {
            it[usedAt] = now
        } > 0

    override suspend fun deleteInactive(now: Long): Boolean = com.devapplab.config.dbQuery {
        SocialLinkAttemptTable.deleteWhere {
            (expiresAt less now) or usedAt.isNotNull()
        } > 0
    }
}

private fun ResultRow.toAttempt() = SocialLinkAttempt(
    id = this[SocialLinkAttemptTable.id],
    tokenHash = this[SocialLinkAttemptTable.tokenHash],
    userId = this[SocialLinkAttemptTable.userId],
    provider = this[SocialLinkAttemptTable.provider],
    issuer = this[SocialLinkAttemptTable.issuer],
    providerSubject = this[SocialLinkAttemptTable.providerSubject],
    mfaCodeId = this[SocialLinkAttemptTable.mfaCodeId],
    passwordVerifiedAt = this[SocialLinkAttemptTable.passwordVerifiedAt],
    expiresAt = this[SocialLinkAttemptTable.expiresAt],
    usedAt = this[SocialLinkAttemptTable.usedAt],
    createdAt = this[SocialLinkAttemptTable.createdAt]
)
