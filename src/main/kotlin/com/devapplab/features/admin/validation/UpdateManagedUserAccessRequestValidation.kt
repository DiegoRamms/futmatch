package com.devapplab.features.admin.validation

import com.devapplab.model.user.request.UpdateManagedUserAccessRequest
import com.devapplab.utils.StringResourcesKey
import io.ktor.server.plugins.requestvalidation.ValidationResult

fun UpdateManagedUserAccessRequest.validate(): ValidationResult {
    return if (role == null && status == null) {
        ValidationResult.Invalid(StringResourcesKey.ADMIN_USER_ACCESS_UPDATE_REQUIRED.value)
    } else {
        ValidationResult.Valid
    }
}
