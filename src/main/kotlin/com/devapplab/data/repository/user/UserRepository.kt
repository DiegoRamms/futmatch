package com.devapplab.data.repository.user

import com.devapplab.model.auth.UserSignInInfo
import com.devapplab.model.payment.PaymentProvider
import com.devapplab.model.user.Gender
import com.devapplab.model.user.PendingUser
import com.devapplab.model.user.PlayerPosition
import com.devapplab.model.user.AdminManagedUsersPage
import com.devapplab.model.user.User
import com.devapplab.model.user.UserBaseInfo
import com.devapplab.model.user.UserHomeProfile
import com.devapplab.model.user.UserRole
import com.devapplab.model.user.UserStatus
import com.devapplab.model.user.response.OrganizerListItem
import java.util.*

interface UserRepository {
    fun create(pendingUser: PendingUser): User
    fun getUserById(userId: UUID): UserBaseInfo?
    fun getHomeProfileById(userId: UUID): UserHomeProfile?
    fun findByEmail(email: String): UserBaseInfo?
    fun isEmailAlreadyRegistered(email: String): Boolean
    suspend fun isPhoneNumberAlreadyRegistered(phone: String): Boolean
    suspend fun isEmailVerified(userId: UUID): Boolean
    fun getUserSignInInfo(email: String): UserSignInInfo?
    fun updatePassword(userId: UUID, hashedPassword: String): Boolean
    fun addUser(user: User): UUID
    suspend fun updateUser(id: UUID, updatedUser: User): Boolean
    suspend fun updateProfilePic(userId: UUID, fileName: String): Boolean
    suspend fun getActiveAdminIds(): List<UUID>
    fun countActiveAdminsTx(): Long
    suspend fun getUserLocalesByIds(userIds: List<UUID>): Map<UUID, String>
    fun updateNameTx(userId: UUID, name: String, lastName: String): Boolean
    fun updateCountryTx(userId: UUID, countryCode: String): Boolean
    fun updateGenderTx(userId: UUID, gender: Gender): Boolean
    fun updatePositionTx(userId: UUID, position: PlayerPosition): Boolean
    fun markEmailAsVerified(userId: UUID): Boolean
    suspend fun deleteUser(id: UUID): Boolean
    suspend fun getPaymentProfile(userId: UUID, provider: PaymentProvider): String?
    suspend fun upsertPaymentProfile(userId: UUID, provider: PaymentProvider, providerCustomerId: String): Boolean
    fun getOrganizers(): List<OrganizerListItem>
    fun getAdminManagedUsers(
        page: Int,
        pageSize: Int,
        roles: Set<UserRole>,
        statuses: Set<UserStatus>
    ): AdminManagedUsersPage
    fun updateManagedUserAccess(userId: UUID, role: UserRole, status: UserStatus): Boolean
}
