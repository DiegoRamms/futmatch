package com.devapplab.model.match.request

import com.devapplab.model.match.TeamType
import kotlinx.serialization.Serializable

@Serializable
data class JoinMatchRequest(
    val team: TeamType? = null
)
