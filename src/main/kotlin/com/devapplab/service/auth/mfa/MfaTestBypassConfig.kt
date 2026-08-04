package com.devapplab.service.auth.mfa

import java.util.UUID

/**
 * TODO: Remove this temporary iOS MFA testing bypass after the test client can complete MFA.
 * Development-only allowlist for testing clients that cannot complete MFA.
 */
data class MfaTestBypassConfig(
    val userIds: Set<UUID> = emptySet()
) {
    fun appliesTo(userId: UUID): Boolean = userId in userIds
}
