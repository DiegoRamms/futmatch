package com.devapplab.model.match.response

import kotlinx.serialization.Serializable

@Serializable
data class PublicMatchesV2Response(
    val region: String,
    val currentVersion: Long,
    val hasChanges: Boolean,
    val matches: List<MatchSummaryResponse>? = null
)

