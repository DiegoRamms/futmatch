package com.devapplab.service.auth.state

import com.devapplab.model.auth.UserSignInInfo
import java.util.UUID

internal sealed interface SignInPreCheck {
    data class Locked(val remainingMinutes: Long) : SignInPreCheck
    data class Ok(val user: UserSignInInfo?) : SignInPreCheck
}

internal data class SignInDeviceDecision(
    val deviceId: UUID,
    val needsMfa: Boolean
)
