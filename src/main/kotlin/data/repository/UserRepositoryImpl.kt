package data.repository

import com.devapplab.data.database.user.UserDao
import com.devapplab.data.repository.UserRepository
import com.devapplab.model.auth.UserSignInInfo
import com.devapplab.model.user.PendingUser
import com.devapplab.model.user.UserBaseInfo
import model.user.User
import java.util.*

class UserRepositoryImpl(private val userDao: UserDao) : UserRepository {

    override fun create(pendingUser: PendingUser): User? {
        return userDao.create(pendingUser)
    }

    override fun getUserById(userId: UUID): UserBaseInfo? {
        return userDao.getUserById(userId)
    }

    override fun findByEmail(email: String): UserBaseInfo? {
        return userDao.findByEmail(email)
    }

    override fun isEmailAlreadyRegistered(email: String): Boolean {
        return userDao.isEmailAlreadyRegistered(email)
    }

    override suspend fun isPhoneNumberAlreadyRegistered(phone: String): Boolean {
        return userDao.isPhoneNumberAlreadyRegistered(phone)
    }

    override suspend fun isEmailVerified(userId: UUID): Boolean {
        return userDao.isEmailVerified(userId)
    }

    override fun getUserSignInInfo(email: String): UserSignInInfo? {
        return userDao.getUserSignInInfo(email)
    }

    override fun updatePassword(userId: UUID, hashedPassword: String): Boolean {
        return userDao.updatePassword(userId, hashedPassword)
    }
}