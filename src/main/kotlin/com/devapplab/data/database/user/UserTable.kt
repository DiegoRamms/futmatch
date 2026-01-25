package com.devapplab.data.database.user

import com.devapplab.model.user.*
import org.jetbrains.exposed.dao.id.UUIDTable

object UserTable : UUIDTable("user") {
    val name = varchar("name", USER_NAME_MAX_LENGTH)
    val lastName = varchar("last_name", USER_LAST_NAME_MAX_LENGTH)
    val email = varchar("email", USER_EMAIL_MAX_LENGTH).uniqueIndex()
    val password = text("password")
    val phone = varchar("phone", USER_PHONE_MAX_LENGTH)
    val status = enumerationByName("status", USER_STATUS_MAX_LENGTH, UserStatus::class)
    val country = varchar("country", USER_COUNTRY_MAX_LENGTH)
    val birthDate = long("birth_date") // Timestamp
    val gender = enumerationByName("gender", 10, Gender::class)
    val playerPosition = enumerationByName("player_position", USER_PLAYER_POSITION_MAX_LENGTH, PlayerPosition::class)
    val profilePic = text("profile_pic").nullable()
    val level = enumerationByName("level", USER_PLAYER_LEVEL_MAX_LENGTH, PlayerLevel::class)
    val isEmailVerified = bool("is_email_verified").default(false)
    val role = enumerationByName("role", USER_ROLE_MAX_LENGTH, UserRole::class)
    val createdAt = long("created_at").default(System.currentTimeMillis())
    val updatedAt = long("updated_at").default(System.currentTimeMillis())
}


