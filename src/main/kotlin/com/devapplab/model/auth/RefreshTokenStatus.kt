package com.devapplab.model.auth

enum class RefreshTokenStatus {
    ACTIVE,
    ROTATED,
    REVOKED,
    REUSE_DETECTED,
    EXPIRED
}
