package com.devapplab.model.auth.request

import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable data class StartSocialLinkRequest(val linkAttemptToken: String, val password: String)
@Serializable data class ConfirmGoogleSocialLinkRequest(val linkAttemptToken: String, val code: String, val idToken: String, @Serializable(with = UUIDSerializer::class) val deviceId: UUID? = null)
@Serializable data class ConfirmAppleSocialLinkRequest(val linkAttemptToken: String, val code: String, val identityToken: String, val nonce: String, val authorizationCode: String, @Serializable(with = UUIDSerializer::class) val deviceId: UUID? = null)
