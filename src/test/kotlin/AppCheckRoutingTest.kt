import com.devapplab.config.appCheck
import com.devapplab.data.repository.device.DesktopDeviceRepositoryImpl
import com.devapplab.data.repository.device.DesktopEnrollmentRepositoryImpl
import com.devapplab.model.AppCheckConfig
import com.devapplab.service.appcheck.FirebaseAppCheckService
import com.devapplab.service.device.DesktopDeviceSecurityService
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.DuplicatePluginException
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * `appCheck { }` children share one transparent selector, so two sibling blocks
 * under the same parent resolve to the same route and install the plugin twice.
 * That crashed the server at startup when the guest routes got their own block.
 */
class AppCheckRoutingTest {

    private val config = AppCheckConfig(enabled = false, enforce = false, projectNumber = "0", allowedAppIds = emptySet())
    private val appCheckService = FirebaseAppCheckService(config)
    private val desktopService = DesktopDeviceSecurityService(
        DesktopDeviceRepositoryImpl(),
        DesktopEnrollmentRepositoryImpl()
    )

    @Test
    fun `two sibling appCheck blocks fail at startup`() {
        assertFailsWith<DuplicatePluginException> {
            testApplication {
                routing {
                    appCheck(appCheckService, config, desktopService) { get("/a") { call.respondText("a") } }
                    appCheck(appCheckService, config, desktopService) { get("/b") { call.respondText("b") } }
                }
                client.get("/a")
            }
        }
    }

    @Test
    fun `auth and guest routes share one appCheck block and both respond`() = testApplication {
        routing {
            appCheck(appCheckService, config, desktopService) {
                get("/auth/ping") { call.respondText("auth") }
                get("/guest/match/ping") { call.respondText("guest") }
            }
        }

        assertEquals(HttpStatusCode.OK, client.get("/auth/ping").status)
        assertEquals(HttpStatusCode.OK, client.get("/guest/match/ping").status)
    }
}
