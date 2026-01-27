package com.devapplab.model.auth

data class JWTConfig(
    val audience: String,
    val issuer: String,
    val realm: String,
    val private: String,
    val public: String,
    val algorithm: String,
    val accessTokenLifetime: Int,
    val refreshTokenLifetime: Int,
    val refreshTokenRotationThreshold: Int
)
