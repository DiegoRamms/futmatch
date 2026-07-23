package com.devapplab.features.admin.validation

import com.devapplab.model.user.request.AdminDeleteUserRequest
import com.devapplab.utils.StringResourcesKey
import io.ktor.server.plugins.requestvalidation.ValidationResult

fun AdminDeleteUserRequest.validate(): ValidationResult =
    if (password.isBlank()) ValidationResult.Invalid(StringResourcesKey.ADMIN_USER_DELETE_PASSWORD_REQUIRED.value)
    else ValidationResult.Valid
