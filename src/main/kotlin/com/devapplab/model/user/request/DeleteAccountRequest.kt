package com.devapplab.model.user.request

import kotlinx.serialization.Serializable

@Serializable
data class DeleteAccountRequest(
    val confirmation: String
) {
    companion object {
        private val requiredConfirmations = setOf(
            "DELETE_MY_ACCOUNT",
            "ELIMINAR_MI_CUENTA"
        )

        fun isValidConfirmation(value: String): Boolean =
            value.trim().uppercase(java.util.Locale.ROOT) in requiredConfirmations
    }
}
