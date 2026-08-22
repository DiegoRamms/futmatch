package com.devapplab.model.auth

data class AppleAuthConfig(
    val clientId: String,
    val teamId: String,
    val keyId: String,
    val privateKeyBase64: String
)
