package com.devapplab.model.field

import model.field.FieldBaseInfo

data class FieldWithImagesBaseInfo(
    val field: FieldBaseInfo,
    val images: List<FieldImage>
)
