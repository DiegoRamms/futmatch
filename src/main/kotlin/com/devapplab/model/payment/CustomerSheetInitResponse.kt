package com.devapplab.model.payment

import kotlinx.serialization.Serializable

@Serializable
data class CustomerSheetInitResponse(
    val customerId: String,
    val customerSessionClientSecret: String,
    val publishableKey: String
)