package com.devapplab.service

import PasswordResetTokenService
import com.devapplab.data.repository.UserRepository
import com.devapplab.model.AppResult
import com.devapplab.model.user.UserBaseInfo
import com.devapplab.model.user.mapper.toUserResponse
import com.devapplab.service.hashing.HashingService

import com.devapplab.utils.createError
import io.ktor.http.*
import model.user.response.UserResponse
import java.util.*

class UserService(
    private val userRepository: UserRepository,
    private val passwordResetTokenService: PasswordResetTokenService,
    private val hashingService: HashingService
) {

    suspend fun getUserById(userId: UUID?, locale: Locale): AppResult<UserResponse> {
        userId ?: return locale.createError(status = HttpStatusCode.NotFound)
        val userResponse: UserBaseInfo =
            userRepository.getUserById(userId) ?: return locale.createError(status = HttpStatusCode.NotFound)
        return AppResult.Success(userResponse.toUserResponse())
    }

//    suspend fun updatePassword(request: UpdatePasswordRequest, locale: Locale): AppResult<Boolean> {
//        val tokenVerificationResult = passwordResetTokenService.verifyResetToken(request.resetToken, locale)
//
//        val userId = when (tokenVerificationResult) {
//            is AppResult.Success -> tokenVerificationResult.data
//            is AppResult.Error -> return AppResult.Error(tokenVerificationResult.status, tokenVerificationResult.message)
//        }
//
//        val hashedPassword = hashingService.hash(request.newPassword)
//        val passwordUpdated = userRepository.updatePassword(userId, hashedPassword)
//
//        if (passwordUpdated) {
//            passwordResetTokenService.invalidateToken(request.resetToken)
//            return AppResult.Success(true)
//        }
//
//        return locale.createError(status = HttpStatusCode.InternalServerError)
//    }
}