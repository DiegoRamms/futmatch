package com.devapplab.model

import kotlinx.serialization.Serializable


@Serializable
data class ErrorResponse(
    val title: String,
    val message: String,
    val errorCode: ErrorCode = ErrorCode.GENERAL_ERROR
)