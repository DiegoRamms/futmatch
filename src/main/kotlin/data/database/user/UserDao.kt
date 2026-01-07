package com.devapplab.data.database.user

import com.devapplab.config.dbQuery
import com.devapplab.model.auth.UserSignInInfo
import com.devapplab.model.user.PendingUser
import com.devapplab.model.user.UserBaseInfo
import model.user.User
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

class UserDao {

    fun addUser(user: User): UUID {
        val result = UserTable.insert {
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
        }
        return result[UserTable.id]
    }

    fun create(pendingUser: PendingUser): User? {
        return UserTable.insert {
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
        }.resultedValues?.singleOrNull()?.let(::rowToUser)
    }

    fun getUserById(id: UUID): UserBaseInfo? {
        return UserTable
            .select(
                UserTable.id,
                UserTable.name,
                UserTable.lastName,
                UserTable.email,
                UserTable.phone,
                UserTable.status,
                UserTable.country,
                UserTable.birthDate,
                UserTable.playerPosition,
                UserTable.profilePic,
                UserTable.level,
                UserTable.createdAt,
                UserTable.updatedAt,
                UserTable.gender,
                UserTable.isEmailVerified,
                UserTable.role
            )
            .where { UserTable.id eq id }
            .mapNotNull(::rowToUserResponse)
            .singleOrNull()
    }

    fun findByEmail(email: String): UserBaseInfo? {
        return UserTable.selectAll().where { UserTable.email eq email }
            .mapNotNull { rowToUserResponse(it) }
            .singleOrNull()
    }

    fun isEmailAlreadyRegistered(email: String): Boolean {
        return !UserTable.select(UserTable.email).where { UserTable.email eq email }.limit(1).empty()
    }

    suspend fun isPhoneNumberAlreadyRegistered(phone: String): Boolean = dbQuery {
        !UserTable.select(UserTable.phone).where { UserTable.phone eq phone }.limit(1).empty()
    }

    suspend fun isEmailVerified(userId: UUID): Boolean = dbQuery {
        val isVerified: Boolean = UserTable.select(UserTable.isEmailVerified)
            .where { UserTable.id eq userId }
            .mapNotNull { resultRow -> resultRow[UserTable.isEmailVerified] }
            .singleOrNull() ?: false

        isVerified
    }

    fun getUserSignInInfo(email: String): UserSignInInfo? {
        val user = UserTable.select(
            UserTable.id,
            UserTable.role,
            UserTable.password,
            UserTable.status,
            UserTable.isEmailVerified
        )
            .where { UserTable.email eq email }.limit(1)
            .mapNotNull { resultRow ->
                UserSignInInfo(
                    resultRow[UserTable.id],
                    resultRow[UserTable.role],
                    resultRow[UserTable.password],
                    resultRow[UserTable.status],
                    resultRow[UserTable.isEmailVerified]
                )
            }.singleOrNull()
        return user
    }

    suspend fun updateUser(id: UUID, updatedUser: User): Boolean = dbQuery {
        val rowsUpdated = UserTable.update({ UserTable.id eq id }) { user ->
            user[name] = updatedUser.name
            user[lastName] = updatedUser.lastName
            user[email] = updatedUser.email
            user[password] = updatedUser.password
            user[phone] = updatedUser.phone
            user[status] = updatedUser.status
            user[country] = updatedUser.country
            user[birthDate] = updatedUser.birthDate
            user[playerPosition] = updatedUser.playerPosition
            user[profilePic] = updatedUser.profilePic
            user[level] = updatedUser.level
            user[updatedAt] = System.currentTimeMillis()
        }
        rowsUpdated > 0
    }

    fun updatePassword(userId: UUID, hashedPassword: String): Boolean {
        return UserTable.update({ UserTable.id eq userId }) {
            it[password] = hashedPassword
            it[updatedAt] = System.currentTimeMillis()
        } > 0
    }

    fun markEmailAsVerified(userId: UUID): Boolean =
        UserTable.update({ UserTable.id eq userId }) {
            it[isEmailVerified] = true
            it[updatedAt] = System.currentTimeMillis()
        } > 0

    suspend fun deleteUser(id: UUID): Boolean = newSuspendedTransaction {
        UserTable.deleteWhere { UserTable.id eq id } > 0
    }

    private fun rowToUser(row: ResultRow): User = User(
        id = row[UserTable.id],
        name = row[UserTable.name],
        lastName = row[UserTable.lastName],
        email = row[UserTable.email],
        password = row[UserTable.password],
        phone = row[UserTable.phone],
        status = row[UserTable.status],
        country = row[UserTable.country],
        birthDate = row[UserTable.birthDate],
        playerPosition = row[UserTable.playerPosition],
        profilePic = row[UserTable.profilePic],
        level = row[UserTable.level],
        createdAt = row[UserTable.createdAt],
        gender = row[UserTable.gender],
        updatedAt = row[UserTable.updatedAt],
        role = row[UserTable.role]
    )

    fun rowToUserResponse(row: ResultRow): UserBaseInfo {
        return UserBaseInfo(
            id = row[UserTable.id],
            name = row[UserTable.name],
            lastName = row[UserTable.lastName],
            email = row[UserTable.email],
            phone = row[UserTable.phone],
            status = row[UserTable.status],
            country = row[UserTable.country],
            birthDate = row[UserTable.birthDate],
            playerPosition = row[UserTable.playerPosition],
            profilePic = row[UserTable.profilePic],
            level = row[UserTable.level],
            createdAt = row[UserTable.createdAt],
            updatedAt = row[UserTable.updatedAt],
            gender = row[UserTable.gender],
            isEmailVerified = row[UserTable.isEmailVerified],
            userRole = row[UserTable.role]
        )
    }
}