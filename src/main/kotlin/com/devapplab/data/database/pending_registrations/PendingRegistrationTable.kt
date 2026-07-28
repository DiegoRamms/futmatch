package com.devapplab.data.database.pending_registrations

import com.devapplab.model.user.*
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object PendingRegistrationTable : Table("pending_registrations") {
    val id = javaUUID("id").autoGenerate()
    val name = varchar("name", USER_NAME_MAX_LENGTH)
    val lastName = varchar("last_name", USER_LAST_NAME_MAX_LENGTH)
    val emailCiphertext = text("email_ciphertext").nullable()
    val emailLookup = varchar("email_lookup", 64).nullable().uniqueIndex()
    val password = varchar("password", 255) // Hashed password
    val phoneCiphertext = text("phone_ciphertext").nullable()
    val phoneLookup = varchar("phone_lookup", 64).nullable().uniqueIndex()
    val piiKeyVersion = varchar("pii_key_version", 32).nullable()
    val country = varchar("country", USER_COUNTRY_MAX_LENGTH)
    val birthDate = long("birth_date") // Unix timestamp
    val playerPosition = enumeration<PlayerPosition>("player_position")
    val gender = enumeration<Gender>("gender")
    val profilePic = varchar("profile_pic", 255).nullable()
    val level = enumeration<PlayerLevel>("level")
    val userRole = enumeration<UserRole>("user_role").default(UserRole.PLAYER)

    val verificationCode = varchar("verification_code", 64)
    val expiresAt = long("expires_at")

    val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }
    val updatedAt = long("updated_at").clientDefault { System.currentTimeMillis() }

    override val primaryKey = PrimaryKey(id)
}
