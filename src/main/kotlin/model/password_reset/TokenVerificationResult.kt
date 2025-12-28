package com.devapplab.model.password_reset

import java.util.UUID

sealed class TokenVerificationResult {
    data class Success(val userId: UUID) : TokenVerificationResult()
    data object Invalid : TokenVerificationResult()
    data object Expired : TokenVerificationResult()
}
