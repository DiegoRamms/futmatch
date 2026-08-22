package com.devapplab.data.database.auth

import com.devapplab.data.database.user.UserTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

/**
 * Holds the Apple refresh token obtained by exchanging the register-time
 * `authorizationCode`, encrypted at rest with the same PII envelope used
 * elsewhere (see [com.devapplab.service.pii.PiiCrypto]). This is the only way
 * to satisfy Apple's account-deletion revocation requirement
 * (`POST https://appleid.apple.com/auth/revoke`) later. Never store the
 * identity token here — it is short-lived and never persisted.
 */
object AppleAuthTokenTable : Table("apple_auth_tokens") {
    val id = javaUUID("id").autoGenerate().uniqueIndex()
    val userId = javaUUID("user_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val refreshTokenCiphertext = text("refresh_token_ciphertext")
    val piiKeyVersion = varchar("pii_key_version", 32)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}
