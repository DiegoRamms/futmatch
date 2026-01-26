package com.devapplab.service.auth.state

import com.devapplab.model.auth.RefreshTokenValidationInfo
import com.devapplab.model.user.UserRole

internal data class RefreshDbData(
    val validationInfo: RefreshTokenValidationInfo,
    val userRole: UserRole
)
