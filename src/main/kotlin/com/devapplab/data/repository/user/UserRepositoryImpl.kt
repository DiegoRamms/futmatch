package com.devapplab.data.repository.user

import com.devapplab.config.dbQuery
import com.devapplab.data.database.user.UserTable
import com.devapplab.model.auth.UserSignInInfo
import com.devapplab.model.user.PendingUser
import com.devapplab.model.user.User
import com.devapplab.model.user.UserBaseInfo
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.*

class UserRepositoryImpl : UserRepository {

    override fun addUser(user: User): UUID {
        return UserTable.insert {
            it[name] = user.name
            it[lastName] = user.lastName
            it[email] = user.email
            it[password] = user.password
            it[phone] = user.phone
            it[status] = user.status
            it[gender] = user.gender
            it[country] = user.country
            it[birthDate] = user.birthDate
            it[playerPosition] = user.playerPosition
            it[profilePic] = user.profilePic
            it[level] = user.level
            it[role] = user.role
        }[UserTable.id]
    }

    override fun create(pendingUser: PendingUser): User {
        val resultRow = UserTable.insert {
            it[name] = pendingUser.name
            it[lastName] = pendingUser.lastName
            it[email] = pendingUser.email
            it[password] = pendingUser.password
            it[phone] = pendingUser.phone
            it[status] = pendingUser.status
            it[gender] = pendingUser.gender
            it[country] = pendingUser.country
            it[birthDate] = pendingUser.birthDate
            it[playerPosition] = pendingUser.playerPosition
            it[profilePic] = pendingUser.profilePic
            it[level] = pendingUser.level
            it[role] = pendingUser.userRole
            it[isEmailVerified] = pendingUser.isEmailVerified
            it[createdAt] = pendingUser.createdAt
            it[updatedAt] = pendingUser.updatedAt
        }.resultedValues?.firstOrNull()  ?: throw IllegalStateException("Create User Error")
        return resultRow.toUser()
    }

    override fun getUserById(userId: UUID): UserBaseInfo? {
        return UserTable.selectAll().where { UserTable.id eq userId }
            .singleOrNull()
            ?.toUserBaseInfo()
    }

    override fun findByEmail(email: String): UserBaseInfo? {
        return UserTable.selectAll().where { UserTable.email eq email }
            .firstOrNull()
            ?.toUserBaseInfo()
    }

    override fun isEmailAlreadyRegistered(email: String): Boolean {
        return UserTable.selectAll().where { UserTable.email eq email }.any()
    }

    override suspend fun isPhoneNumberAlreadyRegistered(phone: String): Boolean = dbQuery {
        UserTable.selectAll().where { UserTable.phone eq phone }.any()
    }

    override suspend fun isEmailVerified(userId: UUID): Boolean = dbQuery {
        UserTable.selectAll().where { UserTable.id eq userId }
            .firstOrNull()
            ?.get(UserTable.isEmailVerified) ?: false
    }

    override fun getUserSignInInfo(email: String): UserSignInInfo? {
        return UserTable.selectAll().where { UserTable.email eq email }.firstOrNull()?.let {
            UserSignInInfo(
                userId = it[UserTable.id],
                userRole = it[UserTable.role],
                password = it[UserTable.password],
                status = it[UserTable.status],
                isEmailVerified = it[UserTable.isEmailVerified]
            )
        }
    }

    override suspend fun updateUser(id: UUID, updatedUser: User): Boolean = dbQuery {
        UserTable.update({ UserTable.id eq id }) {
            it[name] = updatedUser.name
            it[lastName] = updatedUser.lastName
            it[email] = updatedUser.email
            it[password] = updatedUser.password
            it[phone] = updatedUser.phone
            it[status] = updatedUser.status
            it[country] = updatedUser.country
            it[birthDate] = updatedUser.birthDate
            it[playerPosition] = updatedUser.playerPosition
            it[profilePic] = updatedUser.profilePic
            it[level] = updatedUser.level
            it[updatedAt] = System.currentTimeMillis()
        } > 0
    }

    override suspend fun updateProfilePic(userId: UUID, fileName: String): Boolean = dbQuery {
        UserTable.update({ UserTable.id eq userId }) {
            it[profilePic] = fileName
            it[updatedAt] = System.currentTimeMillis()
        } > 0
    }

    override fun updatePassword(userId: UUID, hashedPassword: String): Boolean {
        return UserTable.update({ UserTable.id eq userId }) {
            it[password] = hashedPassword
            it[updatedAt] = System.currentTimeMillis()
        } > 0
    }

    override fun markEmailAsVerified(userId: UUID): Boolean {
        return UserTable.update({ UserTable.id eq userId }) {
            it[isEmailVerified] = true
            it[updatedAt] = System.currentTimeMillis()
        } > 0
    }

    override suspend fun deleteUser(id: UUID): Boolean = dbQuery {
        UserTable.deleteWhere { UserTable.id eq id } > 0
    }

    private fun ResultRow.toUser(): User {
        return User(
            id = this[UserTable.id],
            name = this[UserTable.name],
            lastName = this[UserTable.lastName],
            email = this[UserTable.email],
            password = this[UserTable.password],
            phone = this[UserTable.phone],
            status = this[UserTable.status],
            country = this[UserTable.country],
            birthDate = this[UserTable.birthDate],
            playerPosition = this[UserTable.playerPosition],
            profilePic = this[UserTable.profilePic],
            level = this[UserTable.level],
            createdAt = this[UserTable.createdAt],
            gender = this[UserTable.gender],
            updatedAt = this[UserTable.updatedAt],
            role = this[UserTable.role]
        )
    }

    private fun ResultRow.toUserBaseInfo(): UserBaseInfo {
        return UserBaseInfo(
            id = this[UserTable.id],
            name = this[UserTable.name],
            lastName = this[UserTable.lastName],
            email = this[UserTable.email],
            phone = this[UserTable.phone],
            status = this[UserTable.status],
            country = this[UserTable.country],
            birthDate = this[UserTable.birthDate],
            playerPosition = this[UserTable.playerPosition],
            profilePic = this[UserTable.profilePic],
            level = this[UserTable.level],
            createdAt = this[UserTable.createdAt],
            updatedAt = this[UserTable.updatedAt],
            gender = this[UserTable.gender],
            isEmailVerified = this[UserTable.isEmailVerified],
            userRole = this[UserTable.role]
        )
    }
}