package data.repository

import com.devapplab.config.dbQuery
import com.devapplab.data.database.user.UserDao
import com.devapplab.data.repository.UserRepository
import com.devapplab.model.auth.UserSignInInfo
import com.devapplab.model.user.UserBaseInfo
import java.util.*

class UserRepositoryImpl(private val userDao: UserDao) : UserRepository {

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

    override suspend fun getUserSignInInfo(email: String): UserSignInInfo? = dbQuery {
        userDao.getUserSignInInfo(email)
    }
}