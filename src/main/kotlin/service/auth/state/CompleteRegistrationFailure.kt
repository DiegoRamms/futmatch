package service.auth.state

sealed interface CompleteRegistrationFailure {
    data object PendingNotFound : CompleteRegistrationFailure
    data object Expired : CompleteRegistrationFailure
    data object InvalidCode : CompleteRegistrationFailure
    data object CreateUserFailed : CompleteRegistrationFailure
    data object MissingUserId : CompleteRegistrationFailure
}

class CompleteRegistrationAbort(val reason: CompleteRegistrationFailure) : RuntimeException()