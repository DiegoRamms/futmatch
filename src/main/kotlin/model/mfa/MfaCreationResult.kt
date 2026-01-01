package model.mfa

import java.util.UUID

sealed class MfaCreationResult {
    data class Created(val codeId: UUID, val expiresInSeconds: Long) : MfaCreationResult()
    data class Cooldown(val retryAfterSeconds: Long) : MfaCreationResult()
    data class Locked(val lockDurationMinutes: Int) : MfaCreationResult()
}
