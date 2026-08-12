package com.devapplab.model.admin

import kotlinx.serialization.Serializable

/** Counts displayed by the administrative dashboard. */
@Serializable
data class DashboardSummary(
    val locationsCount: Long,
    val fieldsCount: Long,
    val matchesCount: Long
)
