package com.devapplab

import com.devapplab.model.match.GenderType
import com.devapplab.model.user.Gender
import com.devapplab.model.user.PlayerLevel
import com.devapplab.service.match.MatchVisibilityRules
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MatchVisibilityRulesTest {

    @Test
    fun `advanced user can see lower or equal levels plus any`() {
        assertTrue(
            MatchVisibilityRules.isVisibleFor(
                userGender = Gender.MALE,
                userLevel = PlayerLevel.ADVANCED,
                matchGenderType = GenderType.MIXED,
                matchLevel = PlayerLevel.ANY
            )
        )
        assertTrue(
            MatchVisibilityRules.isVisibleFor(
                userGender = Gender.MALE,
                userLevel = PlayerLevel.ADVANCED,
                matchGenderType = GenderType.MIXED,
                matchLevel = PlayerLevel.INTERMEDIATE
            )
        )
        assertTrue(
            MatchVisibilityRules.isVisibleFor(
                userGender = Gender.MALE,
                userLevel = PlayerLevel.ADVANCED,
                matchGenderType = GenderType.MIXED,
                matchLevel = PlayerLevel.ADVANCED
            )
        )
        assertFalse(
            MatchVisibilityRules.isVisibleFor(
                userGender = Gender.MALE,
                userLevel = PlayerLevel.ADVANCED,
                matchGenderType = GenderType.MIXED,
                matchLevel = PlayerLevel.PROFESSIONAL
            )
        )
    }

    @Test
    fun `beginner user cannot see higher levels`() {
        assertTrue(
            MatchVisibilityRules.isVisibleFor(
                userGender = Gender.MALE,
                userLevel = PlayerLevel.BEGINNER,
                matchGenderType = GenderType.MIXED,
                matchLevel = PlayerLevel.BEGINNER
            )
        )
        assertFalse(
            MatchVisibilityRules.isVisibleFor(
                userGender = Gender.MALE,
                userLevel = PlayerLevel.BEGINNER,
                matchGenderType = GenderType.MIXED,
                matchLevel = PlayerLevel.INTERMEDIATE
            )
        )
    }

    @Test
    fun `gender visibility follows configured rules`() {
        assertTrue(
            MatchVisibilityRules.isVisibleFor(
                userGender = Gender.MALE,
                userLevel = PlayerLevel.ANY,
                matchGenderType = GenderType.MALE_ONLY,
                matchLevel = PlayerLevel.ANY
            )
        )
        assertFalse(
            MatchVisibilityRules.isVisibleFor(
                userGender = Gender.MALE,
                userLevel = PlayerLevel.ANY,
                matchGenderType = GenderType.FEMALE_ONLY,
                matchLevel = PlayerLevel.ANY
            )
        )
        assertTrue(
            MatchVisibilityRules.isVisibleFor(
                userGender = Gender.FEMALE,
                userLevel = PlayerLevel.ANY,
                matchGenderType = GenderType.FEMALE_ONLY,
                matchLevel = PlayerLevel.ANY
            )
        )
        assertFalse(
            MatchVisibilityRules.isVisibleFor(
                userGender = Gender.OTHER,
                userLevel = PlayerLevel.ANY,
                matchGenderType = GenderType.MALE_ONLY,
                matchLevel = PlayerLevel.ANY
            )
        )
        assertTrue(
            MatchVisibilityRules.isVisibleFor(
                userGender = Gender.OTHER,
                userLevel = PlayerLevel.ANY,
                matchGenderType = GenderType.MIXED,
                matchLevel = PlayerLevel.ANY
            )
        )
    }
}
