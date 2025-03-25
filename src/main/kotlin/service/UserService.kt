package com.devapplab.service

import com.devapplab.data.repository.UserRepository
import com.devapplab.model.AppResult
import com.devapplab.model.auth.ClaimConfig
import com.devapplab.model.auth.JWTConfig
import com.devapplab.model.auth.response.AuthCode
import com.devapplab.model.auth.response.AuthResponse
import com.devapplab.model.auth.response.AuthTokenResponse
import com.devapplab.model.user.UserBaseInfo
import com.devapplab.model.user.mapper.toUserResponse
import com.devapplab.service.auth.auth_token.AuthTokenService
import com.devapplab.service.hashing.HashingService
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.createError
import io.ktor.http.*
import model.user.User
import model.user.response.UserResponse
import java.util.*

class UserService(
    private val userRepository: UserRepository,
    private val hashingService: HashingService,
    private val authTokenService: AuthTokenService
) {
    suspend fun addUser(user: User, locale: Locale, jwtConfig: JWTConfig): AppResult<AuthResponse> {
        val isEmailAlreadyRegistered = userRepository.isEmailAlreadyRegistered(user.email)
        val isPhoneNumberAlreadyRegistered = userRepository.isPhoneNumberAlreadyRegistered(user.phone)

        if (isEmailAlreadyRegistered) return locale.respondIsEmailAlreadyRegisteredError()
        if (isPhoneNumberAlreadyRegistered) return locale.respondIsPhoneAlreadyRegisteredError()

        val userWithPasswordHashed = user.copy(password = hashingService.hashPassword(user.password))
        val userId = userRepository.addUser(userWithPasswordHashed)
        val claimConfig = ClaimConfig(userId, false)
        val token = authTokenService.createAuthToken(claimConfig, jwtConfig)
        val authTokenResponse = AuthTokenResponse(token,"")
        val authResponse = AuthResponse(authTokenResponse, authCode = AuthCode.USER_CREATED)

        return AppResult.Success(authResponse)
    }

    suspend fun getUserById(userId: UUID?, locale: Locale): AppResult<UserResponse> {
        userId ?: return locale.createError(status = HttpStatusCode.NotFound)
        val userResponse: UserBaseInfo =
            userRepository.getUserById(userId) ?: return locale.createError(status = HttpStatusCode.NotFound)
        return AppResult.Success(userResponse.toUserResponse())
    }

    private fun Locale.respondIsEmailAlreadyRegisteredError(): AppResult.Failure =
        createError(
            StringResourcesKey.REGISTER_EMAIL_ALREADY_EXISTS_TITLE,
            StringResourcesKey.REGISTER_EMAIL_ALREADY_EXISTS_DESCRIPTION,
            status = HttpStatusCode.Conflict
        )

    private fun Locale.respondIsPhoneAlreadyRegisteredError(): AppResult.Failure =
        createError(
            StringResourcesKey.REGISTER_PHONE_ALREADY_EXISTS_TITLE,
            StringResourcesKey.REGISTER_PHONE_ALREADY_EXISTS_DESCRIPTION,
            status = HttpStatusCode.Conflict
        )
}