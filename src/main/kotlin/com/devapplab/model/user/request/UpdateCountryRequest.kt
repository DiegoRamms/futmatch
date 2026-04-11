package com.devapplab.model.user.request

import kotlinx.serialization.Serializable

@Serializable
data class UpdateCountryRequest(
    val countryCode: String
)
