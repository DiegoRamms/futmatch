package service.auth.state

sealed interface ResendRegistrationCodeDecision {
    data object SendEmail : ResendRegistrationCodeDecision
    data object NotFoundOrExpired : ResendRegistrationCodeDecision
    data object DbUpdateFailed : ResendRegistrationCodeDecision
    data class Cooldown(val remainingSeconds: Long) : ResendRegistrationCodeDecision
}