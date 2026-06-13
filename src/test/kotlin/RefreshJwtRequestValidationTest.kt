package com.devapplab

import com.devapplab.features.auth.validation.validate
import com.devapplab.model.auth.response.RefreshJWTRequest
import io.ktor.server.plugins.requestvalidation.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals

class RefreshJwtRequestValidationTest {

    @Test
    fun `refresh request allows empty body`() {
        val request = RefreshJWTRequest().validate()
        assertEquals(ValidationResult.Valid, request)
    }
}
