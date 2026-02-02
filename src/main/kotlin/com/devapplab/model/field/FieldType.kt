package com.devapplab.model.field

import kotlinx.serialization.Serializable

@Serializable
enum class FieldType {
    NATURAL_GRASS,
    ARTIFICIAL_TURF,
    INDOOR,
    FUTSAL
}
