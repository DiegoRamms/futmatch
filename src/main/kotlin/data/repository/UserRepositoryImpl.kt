package data.repository

import com.devapplab.data.database.user.UserDao
import com.devapplab.data.repository.UserRepository
import com.devapplab.model.user.UserBaseInfo
import model.user.User
import java.util.*

class UserRepositoryImpl(private val userDao: UserDao) : UserRepository {
    override suspend fun addUser(user: User): UUID {
        return userDao.addUser(user)
    }

    override suspend fun getUserById(userId: UUID): UserBaseInfo? {
        return userDao.getUserById(userId)
    }

    override suspend fun isEmailAlreadyRegistered(email: String): Boolean {
        return userDao.isEmailAlreadyRegistered(email)
    }

    override suspend fun isPhoneNumberAlreadyRegistered(phone: String): Boolean {
        return userDao.isPhoneNumberAlreadyRegistered(phone)
    }

    override suspend fun isEmailVerified(userId: UUID): Boolean {
        return userDao.isEmailVerified(userId)
    }
}