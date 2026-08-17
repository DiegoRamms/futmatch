package com.devapplab.data.database.auth

import com.devapplab.data.database.user.UserTable
import com.devapplab.model.auth.identity.AuthProvider
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object AuthIdentityTable : Table("auth_identities") {
    val id = javaUUID("id").autoGenerate().uniqueIndex()
    val userId = javaUUID("user_id").references(UserTable.id)
    val provider = enumerationByName("provider", 32, AuthProvider::class)
    val issuer = varchar("issuer", 255)
    val providerSubject = varchar("provider_subject", 255)
    val createdAt = long("created_at")
    val lastAuthenticatedAt = long("last_authenticated_at")

    init {
        uniqueIndex("auth_identities_provider_subject_unique", provider, issuer, providerSubject)
        uniqueIndex("auth_identities_user_provider_unique", userId, provider)
    }

    override val primaryKey = PrimaryKey(id)
}
