package com.devapplab.data.database.auth

import com.devapplab.data.database.user.UserTable
import com.devapplab.model.auth.identity.AuthProvider
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object SocialLinkAttemptTable : Table("social_link_attempts") {
    val id = javaUUID("id").autoGenerate().uniqueIndex()
    val tokenHash = varchar("token_hash", 128).uniqueIndex()
    val userId = javaUUID("user_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE)
    val provider = enumerationByName("provider", 32, AuthProvider::class)
    val issuer = varchar("issuer", 255)
    val providerSubject = varchar("provider_subject", 255)
    // Exposed cannot express a nullable FK to a javaUUID primary key here; integrity is
    // enforced transactionally when the code is created and consumed.
    val mfaCodeId = javaUUID("mfa_code_id").nullable()
    val passwordVerifiedAt = long("password_verified_at").nullable()
    val expiresAt = long("expires_at")
    val usedAt = long("used_at").nullable()
    val createdAt = long("created_at")

    init { index("social_link_attempt_provider_subject", false, provider, issuer, providerSubject) }
    override val primaryKey = PrimaryKey(id)
}
