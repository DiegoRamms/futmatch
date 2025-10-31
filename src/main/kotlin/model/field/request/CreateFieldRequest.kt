package com.devapplab.model.field.request

import kotlinx.serialization.Serializable

@Serializable
data class CreateFieldRequest(
    val name: String,
    val location: String,
    val price: Double,
    val capacity: Int,
    val description: String,
    val rules: String
)

