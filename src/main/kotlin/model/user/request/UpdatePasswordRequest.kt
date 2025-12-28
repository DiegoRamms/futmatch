package com.devapplab.model.user.request

import kotlinx.serialization.Serializable

@Serializable
data class UpdatePasswordRequest(
    val newPassword: String
)
