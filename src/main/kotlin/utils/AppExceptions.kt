package com.devapplab.utils

class InvalidTokenException(message: String = "Invalid or missing token") : RuntimeException(message)
class AccessDeniedException(message: String = "Access denied") : RuntimeException(message)
class ValueAlreadyExistsException(val value: String) : RuntimeException("A value '$value' already exists.")
class NotFound(message: String = "Not Found") : RuntimeException(message)

class UserBlockedException : Exception()
class UserNotVerifiedException : Exception()
class InvalidPasswordResetRequestException : Exception()