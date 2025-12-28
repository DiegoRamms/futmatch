package com.devapplab.model

import kotlinx.serialization.Serializable


@Serializable
data class ErrorResponse(
    val title: String,
    val message: String,
    val errorCode: ErrorCode = ErrorCode.GENERAL_ERROR
)

@Serializable
enum class ErrorCode {
    GENERAL_ERROR,
    TOO_MANY_REQUESTS,
    AUTH_USER_BLOCKED,
    AUTH_USER_SUSPENDED,
    AUTH_NEED_LOGIN,
    AUTH_EMAIL_NOT_VERIFIED,
    ACCESS_DENIED,
    ALREADY_EXISTS,
    NOT_FOUND,
}
