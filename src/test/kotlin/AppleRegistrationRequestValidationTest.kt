import com.devapplab.features.auth.validation.validate
import com.devapplab.model.auth.request.AppleRegistrationRequest
import com.devapplab.model.user.Gender
import com.devapplab.model.user.PlayerLevel
import com.devapplab.model.user.PlayerPosition
import com.devapplab.utils.StringResourcesKey
import io.ktor.server.plugins.requestvalidation.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AppleRegistrationRequestValidationTest {

    private fun validRequest(
        identityToken: String = "eyJ.token",
        authorizationCode: String = "c1234",
        nonce: String = "raw-nonce",
        name: String = "Diego",
        lastName: String = "Lopez",
        phone: String = "5512345678",
        country: String = "MX",
        birthDate: Long = 946684800000
    ) = AppleRegistrationRequest(
        identityToken = identityToken,
        authorizationCode = authorizationCode,
        nonce = nonce,
        name = name,
        lastName = lastName,
        phone = phone,
        country = country,
        birthDate = birthDate,
        gender = Gender.MALE,
        playerPosition = PlayerPosition.MIDFIELDER,
        level = PlayerLevel.INTERMEDIATE
    )

    @Test
    fun `blank identity token is invalid`() {
        val invalid = assertIs<ValidationResult.Invalid>(validRequest(identityToken = "  ").validate())
        assertEquals(listOf(StringResourcesKey.INVALID_JWT_DESCRIPTION.value), invalid.reasons)
    }

    @Test
    fun `blank authorization code is invalid`() {
        val invalid = assertIs<ValidationResult.Invalid>(validRequest(authorizationCode = "  ").validate())
        assertEquals(listOf(StringResourcesKey.INVALID_JWT_DESCRIPTION.value), invalid.reasons)
    }

    @Test
    fun `blank nonce is invalid`() {
        val invalid = assertIs<ValidationResult.Invalid>(validRequest(nonce = "  ").validate())
        assertEquals(listOf(StringResourcesKey.INVALID_JWT_DESCRIPTION.value), invalid.reasons)
    }

    @Test
    fun `blank name is invalid`() {
        val invalid = assertIs<ValidationResult.Invalid>(validRequest(name = "  ").validate())
        assertEquals(listOf(StringResourcesKey.REGISTER_NAME_INVALID_ERROR.value), invalid.reasons)
    }

    @Test
    fun `invalid phone is invalid`() {
        val invalid = assertIs<ValidationResult.Invalid>(validRequest(phone = "not-a-phone").validate())
        assertEquals(listOf(StringResourcesKey.REGISTER_PHONE_INVALID_ERROR.value), invalid.reasons)
    }

    @Test
    fun `valid request passes`() {
        assertIs<ValidationResult.Valid>(validRequest().validate())
    }
}
