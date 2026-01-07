package com.devapplab.data.repository

import com.devapplab.model.auth.UserSignInInfo
import com.devapplab.model.user.UserBaseInfo
import com.devapplab.model.user.PendingUser
import model.user.User
import java.util.*

interface UserRepository {
    fun create(pendingUser: PendingUser): User?
    fun getUserById(userId: UUID): UserBaseInfo?
    fun findByEmail(email: String): UserBaseInfo?
    fun isEmailAlreadyRegistered(email: String): Boolean
    suspend fun isPhoneNumberAlreadyRegistered(phone: String): Boolean
    suspend fun isEmailVerified(userId: UUID): Boolean
    fun getUserSignInInfo(email: String): UserSignInInfo?
    fun updatePassword(userId: UUID, hashedPassword: String): Boolean
}

