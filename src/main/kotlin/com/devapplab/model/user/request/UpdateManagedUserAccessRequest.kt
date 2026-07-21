package com.devapplab.model.user.request

import com.devapplab.model.user.UserRole
import com.devapplab.model.user.UserStatus
import kotlinx.serialization.Serializable

@Serializable
data class UpdateManagedUserAccessRequest(
    val role: UserRole? = null,
    val status: UserStatus? = null
)
