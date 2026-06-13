package com.devapplab.observability

import io.ktor.server.application.ApplicationCall
import org.slf4j.Logger
import java.util.UUID

typealias AuthRequestContext = AppRequestContext

enum class AuthLogSeverity {
    INFO,
    WARN,
    ERROR
}

fun ApplicationCall.toAuthRequestContext(): AuthRequestContext {
    return requestContext()
}

fun Logger.authEvent(
    severity: AuthLogSeverity,
    event: String,
    context: AuthRequestContext,
    outcome: String,
    reason: String? = null,
    userId: UUID? = null,
    deviceId: UUID? = null,
    statusCode: Int? = null,
    durationMs: Long? = null,
    extra: Map<String, Any?> = emptyMap(),
    throwable: Throwable? = null
) {
    appEvent(
        severity = when (severity) {
            AuthLogSeverity.INFO -> AppLogSeverity.INFO
            AuthLogSeverity.WARN -> AppLogSeverity.WARN
            AuthLogSeverity.ERROR -> AppLogSeverity.ERROR
        },
        event = event,
        context = context,
        outcome = outcome,
        reason = reason,
        userId = userId,
        deviceId = deviceId,
        statusCode = statusCode,
        durationMs = durationMs,
        extra = extra,
        throwable = throwable
    )
}

fun Logger.authSuccess(
    event: String,
    context: AuthRequestContext,
    userId: UUID? = null,
    deviceId: UUID? = null,
    statusCode: Int? = null,
    durationMs: Long? = null,
    extra: Map<String, Any?> = emptyMap()
) = authEvent(
    severity = AuthLogSeverity.INFO,
    event = event,
    context = context,
    outcome = "success",
    userId = userId,
    deviceId = deviceId,
    statusCode = statusCode,
    durationMs = durationMs,
    extra = extra
)

fun Logger.authRejected(
    event: String,
    context: AuthRequestContext,
    reason: String,
    userId: UUID? = null,
    deviceId: UUID? = null,
    statusCode: Int? = null,
    durationMs: Long? = null,
    extra: Map<String, Any?> = emptyMap()
) = authEvent(
    severity = AuthLogSeverity.WARN,
    event = event,
    context = context,
    outcome = "rejected",
    reason = reason,
    userId = userId,
    deviceId = deviceId,
    statusCode = statusCode,
    durationMs = durationMs,
    extra = extra
)

fun Logger.authBlocked(
    event: String,
    context: AuthRequestContext,
    reason: String,
    userId: UUID? = null,
    deviceId: UUID? = null,
    statusCode: Int? = null,
    durationMs: Long? = null,
    extra: Map<String, Any?> = emptyMap()
) = authEvent(
    severity = AuthLogSeverity.WARN,
    event = event,
    context = context,
    outcome = "blocked",
    reason = reason,
    userId = userId,
    deviceId = deviceId,
    statusCode = statusCode,
    durationMs = durationMs,
    extra = extra
)

fun Logger.authFailure(
    event: String,
    context: AuthRequestContext,
    reason: String,
    userId: UUID? = null,
    deviceId: UUID? = null,
    statusCode: Int? = null,
    durationMs: Long? = null,
    extra: Map<String, Any?> = emptyMap(),
    throwable: Throwable? = null
) = authEvent(
    severity = AuthLogSeverity.ERROR,
    event = event,
    context = context,
    outcome = "failed",
    reason = reason,
    userId = userId,
    deviceId = deviceId,
    statusCode = statusCode,
    durationMs = durationMs,
    extra = extra,
    throwable = throwable
)

fun Logger.authMfaRequired(
    context: AuthRequestContext,
    reason: String,
    userId: UUID,
    deviceId: UUID,
    durationMs: Long? = null,
    extra: Map<String, Any?> = emptyMap()
) = authEvent(
    severity = AuthLogSeverity.INFO,
    event = "auth.sign_in.mfa_required",
    context = context,
    outcome = "mfa_required",
    reason = reason,
    userId = userId,
    deviceId = deviceId,
    durationMs = durationMs,
    extra = extra
)

fun Logger.authRotated(
    event: String,
    context: AuthRequestContext,
    userId: UUID? = null,
    deviceId: UUID? = null,
    extra: Map<String, Any?> = emptyMap()
) = authEvent(
    severity = AuthLogSeverity.INFO,
    event = event,
    context = context,
    outcome = "rotated",
    userId = userId,
    deviceId = deviceId,
    extra = extra
)
