import com.devapplab.observability.HomeMetrics
import com.devapplab.observability.HomeSection
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HomeMetricsTest {
    @Test
    fun `records successful and failed operations without changing their result`() = runBlocking {
        val registry = SimpleMeterRegistry()
        val metrics = HomeMetrics(registry)

        assertEquals("ok", metrics.measure(HomeSection.USER) { "ok" })
        assertFailsWith<IllegalStateException> {
            metrics.measure(HomeSection.RANKING) { error("test failure") }
        }

        assertEquals(1, registry.get("user.home.section.duration").tag("section", "user").timer().count())
        assertEquals(1, registry.get("user.home.section.duration").tag("section", "ranking").timer().count())
        assertEquals(0, registry.get("user.home.section.duration").tag("section", "last_match").timer().count())
    }
}
