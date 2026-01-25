package com.devapplab.model.auth.request

import kotlinx.serialization.Serializable

@Serializable
data class CompleteRegistrationRequest(
    val email: String,
    val verificationCode: String
)
