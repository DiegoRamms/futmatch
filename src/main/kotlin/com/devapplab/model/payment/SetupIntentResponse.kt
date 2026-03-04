package com.devapplab.model.payment

import kotlinx.serialization.Serializable


@Serializable
data class SetupIntentResponse(
    val customerId: String,
    val clientSecret: String,
    val publishableKey: String
)