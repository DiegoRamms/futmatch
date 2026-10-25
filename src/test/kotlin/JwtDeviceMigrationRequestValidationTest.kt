import com.devapplab.features.auth.validation.validate
import com.devapplab.features.device.validation.validate
import com.devapplab.model.auth.request.SignOutRequest
import com.devapplab.model.device.DevicePlatform
import com.devapplab.model.device.UpdateFcmTokenRequest
import io.ktor.server.plugins.requestvalidation.ValidationResult
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JwtDeviceMigrationRequestValidationTest {

    @Test
    fun `sign out request allows missing legacy device id during jwt migration`() {
        val migrated = SignOutRequest().validate()
        val legacy = SignOutRequest(deviceId = UUID.randomUUID()).validate()
        val invalid = SignOutRequest(deviceId = UUID(0, 0)).validate()

        assertEquals(ValidationResult.Valid, migrated)
        assertEquals(ValidationResult.Valid, legacy)
        assertTrue(invalid is ValidationResult.Invalid)
    }

    @Test
    fun `device update request allows missing legacy device id during jwt migration`() {
        val migrated = UpdateFcmTokenRequest(
            platform = DevicePlatform.ANDROID,
            fcmToken = "token",
            deviceInfo = "Pixel 9 / Android 16",
            appVersion = "1.0.0",
            osVersion = "16"
        ).validate()

        val legacy = UpdateFcmTokenRequest(
            deviceId = UUID.randomUUID(),
            platform = DevicePlatform.ANDROID,
            fcmToken = "token",
            deviceInfo = "Pixel 9 / Android 16",
            appVersion = "1.0.0",
            osVersion = "16"
        ).validate()

        val invalid = UpdateFcmTokenRequest(
            deviceId = UUID(0, 0),
            platform = DevicePlatform.ANDROID,
            fcmToken = "token",
            deviceInfo = "Pixel 9 / Android 16",
            appVersion = "1.0.0",
            osVersion = "16"
        ).validate()

        assertEquals(ValidationResult.Valid, migrated)
        assertEquals(ValidationResult.Valid, legacy)
        assertTrue(invalid is ValidationResult.Invalid)
    }
}
