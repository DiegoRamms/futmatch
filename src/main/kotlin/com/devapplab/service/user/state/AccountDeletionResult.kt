package com.devapplab.service.user.state

import java.util.UUID

/**
 * Data captured inside the successful account-deletion transaction and consumed
 * immediately after its commit. It is never persisted or logged.
 */
internal data class AccountDeletionResult(
    val email: String,
    val profilePic: String?,
    val cleanupJobId: UUID?
)
