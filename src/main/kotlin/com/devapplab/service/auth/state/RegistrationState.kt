package com.devapplab.service.auth.state

import com.devapplab.model.user.User
import java.util.UUID


internal data class RegistrationTxResult(
    val user: User,
    val deviceId: UUID,
    val userId: UUID
)

