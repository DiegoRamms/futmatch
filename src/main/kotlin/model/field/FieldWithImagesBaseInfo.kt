package com.devapplab.model.field

import model.field.FieldBaseInfo
import model.field.FieldImageBaseInfo

data class FieldWithImagesBaseInfo(
    val field: FieldBaseInfo,
    val images: List<FieldImageBaseInfo>
)
