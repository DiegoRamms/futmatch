package com.devapplab.model

import io.ktor.http.*

sealed class AppResult<out T>(val status: HttpStatusCode = HttpStatusCode.OK) {
    data class Success<T>(val data: T, val appStatus: HttpStatusCode = HttpStatusCode.OK) : AppResult<T>(appStatus)
    data class Failure(val errorResponse: ErrorResponse, val appStatus: HttpStatusCode) : AppResult<Nothing>(appStatus)
}