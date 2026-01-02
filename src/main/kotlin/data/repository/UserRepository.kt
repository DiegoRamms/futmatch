package com.devapplab.data.repository

import com.devapplab.model.auth.UserSignInInfo
import com.devapplab.model.user.UserBaseInfo
import com.devapplab.model.user.PendingUser
import model.user.User
import java.util.*

interface UserRepository {
    suspend fun create(pendingUser: PendingUser): User?
    suspend fun getUserById(userId: UUID): UserBaseInfo?
    suspend fun findByEmail(email: String): UserBaseInfo?
    suspend fun isEmailAlreadyRegistered(email: String): Boolean
    suspend fun isPhoneNumberAlreadyRegistered(phone: String): Boolean
    suspend fun isEmailVerified(userId: UUID): Boolean
    suspend fun getUserSignInInfo(email: String): UserSignInInfo?
    suspend fun updatePassword(userId: UUID, hashedPassword: String): Boolean
}

