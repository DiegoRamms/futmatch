import com.devapplab.observability.AuthMetrics
import com.devapplab.observability.RefreshRejectionReason
import com.devapplab.observability.SecurityAuthEvent
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthMetricsTest {
    @Test
    fun `security counters exist before first event and record independently`() {
        val registry = SimpleMeterRegistry()
        val metrics = AuthMetrics(registry)
        val reuse = registry.get("auth.refresh.rejected").tag("reason", "reuse_detected").counter()
        val invalid = registry.get("auth.security.events").tag("event", "invalid_credentials").counter()
        val mfa = registry.get("auth.security.events").tag("event", "mfa_rejected").counter()
        assertEquals(0.0, reuse.count())
        assertEquals(0.0, invalid.count())
        metrics.recordRefreshRejected(RefreshRejectionReason.REUSE_DETECTED)
        metrics.recordSecurityEvent(SecurityAuthEvent.INVALID_CREDENTIALS)
        assertEquals(1.0, reuse.count())
        assertEquals(1.0, invalid.count())
        assertEquals(0.0, mfa.count())
    }
}
