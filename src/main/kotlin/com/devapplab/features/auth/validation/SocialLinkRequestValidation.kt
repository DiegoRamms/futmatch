package com.devapplab.features.auth.validation

import com.devapplab.model.auth.request.*
import com.devapplab.utils.StringResourcesKey
import io.ktor.server.plugins.requestvalidation.ValidationResult

private fun validToken(value: String) = value.isNotBlank() && value.length <= 12_000
fun StartSocialLinkRequest.validate() = if (validToken(linkAttemptToken) && password.isNotBlank()) ValidationResult.Valid else ValidationResult.Invalid(StringResourcesKey.INVALID_JWT_DESCRIPTION.value)
fun ConfirmGoogleSocialLinkRequest.validate() = if (validToken(linkAttemptToken) && code.isNotBlank() && validToken(idToken)) ValidationResult.Valid else ValidationResult.Invalid(StringResourcesKey.INVALID_JWT_DESCRIPTION.value)
fun ConfirmAppleSocialLinkRequest.validate() = if (validToken(linkAttemptToken) && code.isNotBlank() && validToken(identityToken) && nonce.isNotBlank() && authorizationCode.isNotBlank()) ValidationResult.Valid else ValidationResult.Invalid(StringResourcesKey.INVALID_JWT_DESCRIPTION.value)
