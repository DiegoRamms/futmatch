package com.devapplab.model.auth.response

import kotlinx.serialization.Serializable


@Serializable
data class SignOutResponse(
    val success: Boolean,
    val message: String
)
