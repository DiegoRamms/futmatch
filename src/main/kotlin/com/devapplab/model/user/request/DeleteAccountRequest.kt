package com.devapplab.model.user.request

import kotlinx.serialization.Serializable

@Serializable
data class DeleteAccountRequest(
    val password: String,
    val confirmation: String
) {
    companion object {
        const val REQUIRED_CONFIRMATION = "DELETE_MY_ACCOUNT"
    }
}
