import com.devapplab.features.user.validation.HomeRankingPeriod
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeRankingPeriodTest {
    @Test fun `accepts both valid parameters or neither`() {
        assertEquals(HomeRankingPeriod.Current, HomeRankingPeriod.parse(null, null))
        assertEquals(HomeRankingPeriod.Selected(YearMonth.of(2026, 10)), HomeRankingPeriod.parse("2026", "10"))
        assertEquals(HomeRankingPeriod.Selected(YearMonth.of(1, 1)), HomeRankingPeriod.parse("1", "1"))
        assertEquals(HomeRankingPeriod.Selected(YearMonth.of(9999, 12)), HomeRankingPeriod.parse("9999", "12"))
    }

    @Test fun `rejects missing empty malformed and out of range parameters`() {
        listOf("2026" to null, null to "10", "" to "10", "2026" to " ", "abc" to "10",
            "2026" to "1.5", "0" to "1", "10000" to "1", "2026" to "0", "2026" to "13",
            "999999999999" to "1").forEach { (year, month) ->
            assertEquals(HomeRankingPeriod.Invalid, HomeRankingPeriod.parse(year, month), "$year / $month")
        }
    }
}
