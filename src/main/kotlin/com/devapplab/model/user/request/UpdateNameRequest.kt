package com.devapplab.model.user.request

import kotlinx.serialization.Serializable

@Serializable
data class UpdateNameRequest(
    val name: String,
    val lastName: String
)
