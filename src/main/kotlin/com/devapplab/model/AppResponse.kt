package com.devapplab.model

import kotlinx.serialization.Serializable

@Serializable
data class AppResponse<T>(
    val data: T? = null,
    val error: ErrorResponse? = null,
)
