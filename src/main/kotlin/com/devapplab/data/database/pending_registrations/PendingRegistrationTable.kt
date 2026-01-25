package com.devapplab.data.database.pending_registrations

import com.devapplab.model.user.*
import org.jetbrains.exposed.sql.Table

object PendingRegistrationTable : Table("pending_registrations") {
    val id = uuid("id").autoGenerate()
    val name = varchar("name", USER_NAME_MAX_LENGTH)
    val lastName = varchar("last_name", USER_LAST_NAME_MAX_LENGTH)
    val email = varchar("email", USER_EMAIL_MAX_LENGTH).uniqueIndex()
    val password = varchar("password", 255) // Hashed password
    val phone = varchar("phone", USER_PHONE_MAX_LENGTH)
    val country = varchar("country", USER_COUNTRY_MAX_LENGTH)
    val birthDate = long("birth_date") // Unix timestamp
    val playerPosition = enumeration<PlayerPosition>("player_position")
    val gender = enumeration<Gender>("gender")
    val profilePic = varchar("profile_pic", 255).nullable()
    val level = enumeration<PlayerLevel>("level")
    val userRole = enumeration<UserRole>("user_role").default(UserRole.PLAYER)

    val verificationCode = varchar("verification_code", 64)
    val expiresAt = long("expires_at")

    val createdAt = long("created_at").default(System.currentTimeMillis())
    val updatedAt = long("updated_at").default(System.currentTimeMillis())

    override val primaryKey = PrimaryKey(id)
}
