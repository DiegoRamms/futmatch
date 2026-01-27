package com.devapplab.model.auth.request

import kotlinx.serialization.Serializable

@Serializable
data class ResendRegistrationCodeRequest(
    val email: String
)
