package data.repository.user

import com.devapplab.config.dbQuery
import com.devapplab.data.database.user.UserTable
import com.devapplab.data.repository.user.UserRepository
import com.devapplab.model.auth.UserSignInInfo
import com.devapplab.model.user.PendingUser
import com.devapplab.model.user.UserBaseInfo
import data.database.user.UserDAO
import model.user.User
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

class UserRepositoryImpl : UserRepository {

    override fun addUser(user: User): UUID {
        return transaction {
            UserDAO.new {
                name = user.name
                lastName = user.lastName
                email = user.email
                password = user.password
                phone = user.phone
                status = user.status
                gender = user.gender
                country = user.country
                birthDate = user.birthDate
                playerPosition = user.playerPosition
                profilePic = user.profilePic
                level = user.level
                role = user.role
            }.id.value
        }
    }

    override fun create(pendingUser: PendingUser): User {
        return transaction {
            UserDAO.new {
                name = pendingUser.name
                lastName = pendingUser.lastName
                email = pendingUser.email
                password = pendingUser.password
                phone = pendingUser.phone
                status = pendingUser.status
                gender = pendingUser.gender
                country = pendingUser.country
                birthDate = pendingUser.birthDate
                playerPosition = pendingUser.playerPosition
                profilePic = pendingUser.profilePic
                level = pendingUser.level
                role = pendingUser.userRole
                isEmailVerified = pendingUser.isEmailVerified
                createdAt = pendingUser.createdAt
                updatedAt = pendingUser.updatedAt
            }.toUser()
        }
    }

    override fun getUserById(userId: UUID): UserBaseInfo? {
        return transaction {
            UserDAO.findById(userId)?.toUserBaseInfo()
        }
    }

    override fun findByEmail(email: String): UserBaseInfo? {
        return transaction {
            UserDAO.find { UserTable.email eq email }.firstOrNull()?.toUserBaseInfo()
        }
    }

    override fun isEmailAlreadyRegistered(email: String): Boolean {
        return transaction {
            UserDAO.find { UserTable.email eq email }.firstOrNull() != null
        }
    }

    override suspend fun isPhoneNumberAlreadyRegistered(phone: String): Boolean = dbQuery {
        UserDAO.find { UserTable.phone eq phone }.firstOrNull() != null
    }

    override suspend fun isEmailVerified(userId: UUID): Boolean = dbQuery {
        UserDAO.findById(userId)?.isEmailVerified ?: false
    }

    override fun getUserSignInInfo(email: String): UserSignInInfo? {
        return transaction {
            UserDAO.find { UserTable.email eq email }.firstOrNull()?.let {
                UserSignInInfo(
                    userId = it.id.value,
                    userRole = it.role,
                    password = it.password,
                    status = it.status,
                    isEmailVerified = it.isEmailVerified
                )
            }
        }
    }

    override suspend fun updateUser(id: UUID, updatedUser: User): Boolean = dbQuery {
        val user = UserDAO.findById(id) ?: return@dbQuery false
        user.apply {
            name = updatedUser.name
            lastName = updatedUser.lastName
            email = updatedUser.email
            password = updatedUser.password
            phone = updatedUser.phone
            status = updatedUser.status
            country = updatedUser.country
            birthDate = updatedUser.birthDate
            playerPosition = updatedUser.playerPosition
            profilePic = updatedUser.profilePic
            level = updatedUser.level
            updatedAt = System.currentTimeMillis()
        }
        true
    }

    override fun updatePassword(userId: UUID, hashedPassword: String): Boolean {
        return transaction {
            UserDAO.findById(userId)?.apply {
                password = hashedPassword
                updatedAt = System.currentTimeMillis()
            } != null
        }
    }

    override fun markEmailAsVerified(userId: UUID): Boolean {
        return transaction {
            UserDAO.findById(userId)?.apply {
                isEmailVerified = true
                updatedAt = System.currentTimeMillis()
            } != null
        }
    }

    override suspend fun deleteUser(id: UUID): Boolean = dbQuery {
        UserDAO.findById(id)?.delete()
        true
    }

    private fun UserDAO.toUser(): User {
        return User(
            id = id.value,
            name = name,
            lastName = lastName,
            email = email,
            password = password,
            phone = phone,
            status = status,
            country = country,
            birthDate = birthDate,
            playerPosition = playerPosition,
            profilePic = profilePic,
            level = level,
            createdAt = createdAt,
            gender = gender,
            updatedAt = updatedAt,
            role = role
        )
    }

    private fun UserDAO.toUserBaseInfo(): UserBaseInfo {
        return UserBaseInfo(
            id = id.value,
            name = name,
            lastName = lastName,
            email = email,
            phone = phone,
            status = status,
            country = country,
            birthDate = birthDate,
            playerPosition = playerPosition,
            profilePic = profilePic,
            level = level,
            createdAt = createdAt,
            updatedAt = updatedAt,
            gender = gender,
            isEmailVerified = isEmailVerified,
            userRole = role
        )
    }
}