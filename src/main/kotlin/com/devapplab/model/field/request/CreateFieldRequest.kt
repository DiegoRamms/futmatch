package com.devapplab.model.field.request

import kotlinx.serialization.Serializable

@Serializable
data class CreateFieldRequest(
    val name: String,
    val priceInCents: Long,
    val capacity: Int,
    val description: String,
    val rules: String
)