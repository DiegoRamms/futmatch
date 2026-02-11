package com.devapplab.model

import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
sealed class AppResult<out T> {
    @Suppress("TRANSIENT_IS_REDUNDANT")
    @Transient
    abstract val status: HttpStatusCode

    @Serializable
    data class Success<T>(val data: T, @Transient val appStatus: HttpStatusCode = HttpStatusCode.OK) : AppResult<T>() {
        override val status: HttpStatusCode get() = appStatus
    }

    @Serializable
    data class Failure(val errorResponse: ErrorResponse, @Transient val appStatus: HttpStatusCode = HttpStatusCode.InternalServerError) : AppResult<Nothing>() {
        override val status: HttpStatusCode get() = appStatus
    }
}