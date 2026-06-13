package com.devapplab

import com.devapplab.features.auth.validation.validate
import com.devapplab.model.auth.response.RefreshJWTRequest
import io.ktor.server.plugins.requestvalidation.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RefreshJwtRequestValidationTest {

    @Test
    fun `refresh request allows empty body during migration`() {
        val migrated = RefreshJWTRequest().validate()
        val legacy = RefreshJWTRequest(
            userId = java.util.UUID.randomUUID(),
            deviceId = java.util.UUID.randomUUID()
        ).validate()

        assertEquals(ValidationResult.Valid, migrated)
        assertEquals(ValidationResult.Valid, legacy)
    }

    @Test
    fun `refresh request rejects partial legacy payload`() {
        val missingDevice = RefreshJWTRequest(userId = java.util.UUID.randomUUID()).validate()
        val missingUser = RefreshJWTRequest(deviceId = java.util.UUID.randomUUID()).validate()

        assertTrue(missingDevice is ValidationResult.Invalid)
        assertTrue(missingUser is ValidationResult.Invalid)
    }
}
