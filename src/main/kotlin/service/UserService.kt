package com.devapplab.service

import com.devapplab.data.repository.UserRepository
import com.devapplab.model.AppResult
import com.devapplab.model.user.UserBaseInfo
import com.devapplab.model.user.mapper.toUserResponse
import com.devapplab.utils.createError
import io.ktor.http.*
import model.user.response.UserResponse
import java.util.*

class UserService(
    private val userRepository: UserRepository
) {

    suspend fun getUserById(userId: UUID?, locale: Locale): AppResult<UserResponse> {
        userId ?: return locale.createError(status = HttpStatusCode.NotFound)
        val userResponse: UserBaseInfo =
            userRepository.getUserById(userId) ?: return locale.createError(status = HttpStatusCode.NotFound)
        return AppResult.Success(userResponse.toUserResponse())
    }

}