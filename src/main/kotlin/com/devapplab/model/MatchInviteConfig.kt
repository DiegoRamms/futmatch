package com.devapplab.model

data class MatchInviteConfig(
    val enabled: Boolean = true,
    /** Hours before kick-off at which invites go out, e.g. [48, 24, 6, 2]. */
    val milestonesHours: List<Int> = DEFAULT_MILESTONES_HOURS,
    val intervalMinutes: Long = 15,
) {
    companion object {
        val DEFAULT_MILESTONES_HOURS = listOf(48, 24, 6, 2)

        fun parseMilestones(raw: String?): List<Int> =
            parseMilestonesOrNull(raw) ?: DEFAULT_MILESTONES_HOURS

        /** Parses "48,24,6"; returns null when there is no valid positive hour in [raw]. */
        fun parseMilestonesOrNull(raw: String?): List<Int>? =
            raw?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?.filter { it > 0 }
                ?.distinct()
                ?.sortedDescending()
                ?.takeIf { it.isNotEmpty() }
    }
}
