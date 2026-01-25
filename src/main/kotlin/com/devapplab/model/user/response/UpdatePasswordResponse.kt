package com.devapplab.model.user.response

import kotlinx.serialization.Serializable

@Serializable
data class UpdatePasswordResponse(
    val success: Boolean,
    val message: String
)
