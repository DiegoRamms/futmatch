package com.devapplab.service.auth.state

internal sealed interface UpdatePasswordTxResult {
    data object InvalidToken : UpdatePasswordTxResult
    data object ExpiredToken : UpdatePasswordTxResult
    data object UserNotFound : UpdatePasswordTxResult
    data object UpdateFailed : UpdatePasswordTxResult
    data object Success : UpdatePasswordTxResult
}

internal sealed interface ResetMfaResult {
    data object UserNotFound : ResetMfaResult
    data object InvalidCode : ResetMfaResult
    data class Success(val resetToken: String) : ResetMfaResult
}
