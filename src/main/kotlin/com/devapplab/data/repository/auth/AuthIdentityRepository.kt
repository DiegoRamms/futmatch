package com.devapplab.data.repository.auth

import com.devapplab.data.database.auth.AuthIdentityTable
import com.devapplab.model.auth.identity.AuthIdentity
import com.devapplab.model.auth.identity.AuthProvider
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

interface AuthIdentityRepository {
    fun findByProviderSubjectTx(
        provider: AuthProvider,
        issuer: String,
        providerSubject: String
    ): AuthIdentity?

    fun createTx(identity: AuthIdentity): AuthIdentity

    fun updateLastAuthenticatedAtTx(identityId: java.util.UUID, authenticatedAt: Long): Boolean
}

class AuthIdentityRepositoryImpl : AuthIdentityRepository {
    override fun findByProviderSubjectTx(
        provider: AuthProvider,
        issuer: String,
        providerSubject: String
    ): AuthIdentity? {
        return AuthIdentityTable
            .selectAll()
            .where {
                (AuthIdentityTable.provider eq provider) and
                    (AuthIdentityTable.issuer eq issuer) and
                    (AuthIdentityTable.providerSubject eq providerSubject)
            }
            .singleOrNull()
            ?.toAuthIdentity()
    }

    override fun createTx(identity: AuthIdentity): AuthIdentity {
        val row = AuthIdentityTable.insert {
            it[userId] = identity.userId
            it[provider] = identity.provider
            it[issuer] = identity.issuer
            it[providerSubject] = identity.providerSubject
            it[createdAt] = identity.createdAt
            it[lastAuthenticatedAt] = identity.lastAuthenticatedAt
        }.resultedValues?.singleOrNull()
            ?: error("Unable to create authentication identity")

        return row.toAuthIdentity()
    }

    override fun updateLastAuthenticatedAtTx(identityId: java.util.UUID, authenticatedAt: Long): Boolean {
        return AuthIdentityTable.update({ AuthIdentityTable.id eq identityId }) {
            it[lastAuthenticatedAt] = authenticatedAt
        } > 0
    }
}

private fun ResultRow.toAuthIdentity(): AuthIdentity = AuthIdentity(
    id = this[AuthIdentityTable.id],
    userId = this[AuthIdentityTable.userId],
    provider = this[AuthIdentityTable.provider],
    issuer = this[AuthIdentityTable.issuer],
    providerSubject = this[AuthIdentityTable.providerSubject],
    createdAt = this[AuthIdentityTable.createdAt],
    lastAuthenticatedAt = this[AuthIdentityTable.lastAuthenticatedAt]
)
