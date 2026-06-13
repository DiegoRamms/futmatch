package com.devapplab

import com.devapplab.features.auth.validation.validate
import com.devapplab.model.mfa.MfaCodeRequest
import com.devapplab.model.mfa.MfaCodeVerificationRequest
import io.ktor.server.plugins.requestvalidation.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MfaChallengeRequestValidationTest {

    @Test
    fun `mfa send request requires non blank challenge token`() {
        val invalid = MfaCodeRequest("").validate()
        val valid = MfaCodeRequest("challenge-token").validate()
        val legacy = MfaCodeRequest(userId = java.util.UUID.randomUUID(), deviceId = java.util.UUID.randomUUID()).validate()

        assertTrue(invalid is ValidationResult.Invalid)
        assertEquals(ValidationResult.Valid, valid)
        assertEquals(ValidationResult.Valid, legacy)
    }

    @Test
    fun `mfa verify request requires challenge token and code`() {
        val invalidToken = MfaCodeVerificationRequest("", code = "123456").validate()
        val invalidCode = MfaCodeVerificationRequest("challenge-token", code = "").validate()
        val valid = MfaCodeVerificationRequest("challenge-token", code = "123456").validate()
        val legacy = MfaCodeVerificationRequest(
            userId = java.util.UUID.randomUUID(),
            deviceId = java.util.UUID.randomUUID(),
            code = "123456"
        ).validate()

        assertTrue(invalidToken is ValidationResult.Invalid)
        assertTrue(invalidCode is ValidationResult.Invalid)
        assertEquals(ValidationResult.Valid, valid)
        assertEquals(ValidationResult.Valid, legacy)
    }
}
