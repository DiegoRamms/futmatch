package com.devapplab.model.user.request

import kotlinx.serialization.Serializable

@Serializable
data class UpdateGenderRequest(
    val gender: String
)
