package com.devapplab.model

import kotlinx.serialization.Serializable


@Serializable
data class ErrorResponse(
    val title: String,
    val message: String,
    val errorCode: ErrorCode = ErrorCode.GENERAL_ERROR
)

enum class ErrorCode(val code: String) {
    GENERAL_ERROR("GENERAL_ERROR"),
    AUTH_USER_BLOCKED("AUTH_USER_BLOCKED"),
    AUTH_USER_SUSPENDED("AUTH_USER_SUSPENDED"),
    AUTH_NEED_LOGIN("AUTH_NEED_LOGIN"),
}
