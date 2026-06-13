package com.devapplab.model.auth

enum class RefreshTokenStatusReason {
    TOKEN_ISSUED,
    TOKEN_ROTATED,
    SIGN_OUT,
    SECURITY_REUSE,
    ADMIN_REVOCATION,
    USER_FORCED_LOGOUT,
    TOKEN_EXPIRED
}
