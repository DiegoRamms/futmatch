package com.devapplab.features.user.validation

import java.time.YearMonth

sealed interface HomeRankingPeriod {
    data object Current : HomeRankingPeriod
    data object Invalid : HomeRankingPeriod
    data class Selected(val month: YearMonth) : HomeRankingPeriod

    companion object {
        fun parse(year: String?, month: String?): HomeRankingPeriod {
            if (year == null && month == null) return Current
            val parsedYear = year?.trim()?.toIntOrNull()
            val parsedMonth = month?.trim()?.toIntOrNull()
            if (parsedYear == null || parsedMonth == null || parsedYear !in 1..9999 || parsedMonth !in 1..12) return Invalid
            return Selected(YearMonth.of(parsedYear, parsedMonth))
        }
    }
}
