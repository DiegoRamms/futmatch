package com.devapplab.service.match

import com.devapplab.model.match.GenderType
import com.devapplab.model.user.Gender
import com.devapplab.model.user.PlayerLevel

object MatchVisibilityRules {

    fun isVisibleFor(
        userGender: Gender,
        userLevel: PlayerLevel,
        matchGenderType: GenderType,
        matchLevel: PlayerLevel
    ): Boolean {
        return isGenderVisible(userGender, matchGenderType) && isLevelVisible(userLevel, matchLevel)
    }

    private fun isGenderVisible(userGender: Gender, matchGenderType: GenderType): Boolean {
        return when (userGender) {
            Gender.MALE -> matchGenderType == GenderType.MIXED || matchGenderType == GenderType.MALE_ONLY
            Gender.FEMALE -> matchGenderType == GenderType.MIXED || matchGenderType == GenderType.FEMALE_ONLY
            Gender.OTHER -> matchGenderType == GenderType.MIXED
        }
    }

    private fun isLevelVisible(userLevel: PlayerLevel, matchLevel: PlayerLevel): Boolean {
        if (matchLevel == PlayerLevel.ANY || userLevel == PlayerLevel.ANY) return true
        return levelRank(matchLevel) <= levelRank(userLevel)
    }

    private fun levelRank(level: PlayerLevel): Int {
        return when (level) {
            PlayerLevel.ANY -> 0
            PlayerLevel.BEGINNER -> 1
            PlayerLevel.INTERMEDIATE -> 2
            PlayerLevel.ADVANCED -> 3
            PlayerLevel.PROFESSIONAL -> 4
        }
    }
}
