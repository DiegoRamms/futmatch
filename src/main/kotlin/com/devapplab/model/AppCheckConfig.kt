package com.devapplab.model

data class AppCheckConfig(
    val enabled: Boolean,
    val enforce: Boolean,
    val projectNumber: String,
    val allowedAppIds: Set<String>
)
