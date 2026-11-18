package com.devapplab.model.user.request

import kotlinx.serialization.Serializable

@Serializable
data class UpdateNotificationPreferencesRequest(
    val matchInvitesEnabled: Boolean
)
