package com.devapplab

import com.devapplab.features.user.validation.validate
import com.devapplab.model.user.request.DeleteAccountRequest
import com.devapplab.utils.StringResourcesKey
import io.ktor.server.plugins.requestvalidation.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DeleteAccountRequestValidationTest {

    @Test
    fun `accepts English confirmation regardless of case and surrounding whitespace`() {
        assertEquals(
            ValidationResult.Valid,
            DeleteAccountRequest("  delete_my_account  ").validate()
        )
    }

    @Test
    fun `accepts Spanish confirmation regardless of case and surrounding whitespace`() {
        assertEquals(
            ValidationResult.Valid,
            DeleteAccountRequest("  eliminar_mi_cuenta  ").validate()
        )
    }

    @Test
    fun `rejects a confirmation that does not exactly match an accepted phrase`() {
        val result = DeleteAccountRequest("DELETE ACCOUNT").validate()
        val invalid = assertIs<ValidationResult.Invalid>(result)

        assertEquals(listOf(StringResourcesKey.ACCOUNT_DELETION_CONFIRMATION_REQUIRED.value), invalid.reasons)
    }
}
