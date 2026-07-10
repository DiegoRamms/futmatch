package com.devapplab.model.field.request

import com.devapplab.model.field.FieldType
import com.devapplab.model.field.FootwearType
import kotlinx.serialization.Serializable

@Serializable
data class CreateFieldRequest(
    val name: String,
    val priceInCents: Long,
    val organizerFeeInCents: Long = 20_000,
    val minimumProfitOverrideInCents: Long? = null,
    val maxPricePerPlayerOverrideInCents: Long? = null,
    val capacity: Int,
    val description: String,
    val rules: String,
    val footwearType: FootwearType? = null,
    val fieldType: FieldType? = null,
    val hasParking: Boolean = false,
    val extraInfo: String? = null
)
