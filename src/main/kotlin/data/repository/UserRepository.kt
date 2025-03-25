package com.devapplab.data.repository

import com.devapplab.model.user.UserBaseInfo
import model.user.User
import java.util.*

interface UserRepository {
    suspend fun addUser(user: User): UUID
    suspend fun getUserById(userId: UUID): UserBaseInfo?
    suspend fun isEmailAlreadyRegistered(email: String): Boolean
    suspend fun isPhoneNumberAlreadyRegistered(phone: String): Boolean
}

