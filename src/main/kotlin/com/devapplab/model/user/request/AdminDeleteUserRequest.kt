package com.devapplab.model.user.request

import kotlinx.serialization.Serializable

@Serializable
data class AdminDeleteUserRequest(val password: String)
