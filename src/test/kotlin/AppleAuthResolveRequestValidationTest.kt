import com.devapplab.features.auth.validation.validate
import com.devapplab.model.auth.request.AppleAuthResolveRequest
import com.devapplab.utils.StringResourcesKey
import io.ktor.server.plugins.requestvalidation.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AppleAuthResolveRequestValidationTest {

    @Test
    fun `blank identity token is invalid`() {
        val result = AppleAuthResolveRequest(identityToken = "   ", nonce = "nonce").validate()
        val invalid = assertIs<ValidationResult.Invalid>(result)
        assertEquals(listOf(StringResourcesKey.INVALID_JWT_DESCRIPTION.value), invalid.reasons)
    }

    @Test
    fun `identity token longer than max length is invalid`() {
        val result = AppleAuthResolveRequest(identityToken = "a".repeat(12_001), nonce = "nonce").validate()
        val invalid = assertIs<ValidationResult.Invalid>(result)
        assertEquals(listOf(StringResourcesKey.INVALID_JWT_DESCRIPTION.value), invalid.reasons)
    }

    @Test
    fun `blank nonce is invalid`() {
        val result = AppleAuthResolveRequest(identityToken = "eyJ.token", nonce = "   ").validate()
        val invalid = assertIs<ValidationResult.Invalid>(result)
        assertEquals(listOf(StringResourcesKey.INVALID_JWT_DESCRIPTION.value), invalid.reasons)
    }

    @Test
    fun `valid request passes`() {
        val result = AppleAuthResolveRequest(identityToken = "eyJ.token", nonce = "raw-nonce").validate()
        assertIs<ValidationResult.Valid>(result)
    }
}
