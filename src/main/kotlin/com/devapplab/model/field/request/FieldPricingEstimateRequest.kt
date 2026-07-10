package com.devapplab.model.field.request

import kotlinx.serialization.Serializable

@Serializable
data class FieldPricingEstimateRequest(
    val maxPlayers: Int
)

@Serializable
data class FieldPricingCustomRequest(
    val maxPlayers: Int,
    val pricePerPlayerInCents: Long
)
