package com.devapplab.model

data class MatchInviteConfig(
    val enabled: Boolean = true,
    /** Hours before kick-off at which invites go out, e.g. [24, 6, 2]. */
    val milestonesHours: List<Int> = listOf(24, 6, 2),
    val intervalMinutes: Long = 15,
) {
    companion object {
        fun parseMilestones(raw: String?): List<Int> =
            raw?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?.filter { it > 0 }
                ?.distinct()
                ?.sortedDescending()
                ?.takeIf { it.isNotEmpty() }
                ?: listOf(24, 6, 2)
    }
}
