package com.devapplab.model.match.request

import kotlinx.serialization.Serializable

@Serializable
data class CancelMatchRequest(
    val reason: String
)
