import com.devapplab.model.MatchInviteConfig
import com.devapplab.service.match.MatchInviteMilestonesProvider
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class MatchInviteMilestonesProviderTest {
    private val localConfig = MatchInviteConfig(milestonesHours = listOf(24, 6, 2))

    private fun provider(vararg responses: () -> String?): MatchInviteMilestonesProvider {
        val queue = ArrayDeque(responses.toList())
        return MatchInviteMilestonesProvider(localConfig) { queue.removeFirst().invoke() }
    }

    @Test
    fun `remote value wins and is parsed`() = runBlocking {
        assertEquals(listOf(48, 24, 6), provider({ " 6,48, 24" }).milestonesHours())
    }

    @Test
    fun `missing parameter or Firebase not initialized uses the local config`() = runBlocking {
        val p = provider({ null }, { "" })
        assertEquals(listOf(24, 6, 2), p.milestonesHours())
        assertEquals(listOf(24, 6, 2), p.milestonesHours())
    }

    @Test
    fun `fetch error or invalid value keeps the last valid remote value`() = runBlocking {
        val p = provider({ "48,24,6" }, { error("network down") }, { "abc,0" })
        assertEquals(listOf(48, 24, 6), p.milestonesHours())
        assertEquals(listOf(48, 24, 6), p.milestonesHours())
        assertEquals(listOf(48, 24, 6), p.milestonesHours())
    }

    @Test
    fun `fetch error before any remote value uses the local config`() = runBlocking {
        assertEquals(listOf(24, 6, 2), provider({ error("network down") }).milestonesHours())
    }

    @Test
    fun `removing the parameter goes back to the local config`() = runBlocking {
        val p = provider({ "12" }, { "" })
        assertEquals(listOf(12), p.milestonesHours())
        assertEquals(listOf(24, 6, 2), p.milestonesHours())
    }
}
