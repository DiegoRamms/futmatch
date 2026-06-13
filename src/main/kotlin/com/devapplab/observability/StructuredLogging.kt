package com.devapplab.observability

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.slf4j.Logger
import java.util.UUID

enum class AppLogSeverity {
    INFO,
    WARN,
    ERROR
}

fun Logger.appEvent(
    severity: AppLogSeverity,
    event: String,
    context: AppRequestContext,
    outcome: String,
    reason: String? = null,
    userId: UUID? = null,
    deviceId: UUID? = null,
    statusCode: Int? = null,
    durationMs: Long? = null,
    extra: Map<String, Any?> = emptyMap(),
    throwable: Throwable? = null
) {
    val payload = buildJsonObject {
        put("event", JsonPrimitive(event))
        put("severity", JsonPrimitive(severity.name))
        put("requestId", JsonPrimitive(context.requestId))
        put("method", JsonPrimitive(context.method))
        put("path", JsonPrimitive(context.path))
        put("outcome", JsonPrimitive(outcome))
        reason?.let { put("reason", JsonPrimitive(it)) }
        userId?.let { put("userId", JsonPrimitive(it.toString())) }
        deviceId?.let { put("deviceId", JsonPrimitive(it.toString())) }
        statusCode?.let { put("statusCode", JsonPrimitive(it)) }
        durationMs?.let { put("durationMs", JsonPrimitive(it)) }
        context.platform?.let { put("platform", JsonPrimitive(it)) }
        context.appVersion?.let { put("appVersion", JsonPrimitive(it)) }
        context.buildNumber?.let { put("buildNumber", JsonPrimitive(it)) }
        context.osVersion?.let { put("osVersion", JsonPrimitive(it)) }
        context.deviceModel?.let { put("deviceModel", JsonPrimitive(it)) }
        extra.forEach { (key, value) ->
            when (value) {
                null -> put(key, JsonPrimitive("null"))
                is String -> put(key, JsonPrimitive(value))
                is Boolean -> put(key, JsonPrimitive(value))
                is Int -> put(key, JsonPrimitive(value))
                is Long -> put(key, JsonPrimitive(value))
                is Float -> put(key, JsonPrimitive(value))
                is Double -> put(key, JsonPrimitive(value))
                is UUID -> put(key, JsonPrimitive(value.toString()))
                else -> put(key, JsonPrimitive(value.toString()))
            }
        }
    }.toString()

    when (severity) {
        AppLogSeverity.INFO -> if (throwable == null) info(payload) else info(payload, throwable)
        AppLogSeverity.WARN -> if (throwable == null) warn(payload) else warn(payload, throwable)
        AppLogSeverity.ERROR -> if (throwable == null) error(payload) else error(payload, throwable)
    }
}

fun Logger.appSuccess(
    event: String,
    context: AppRequestContext,
    userId: UUID? = null,
    deviceId: UUID? = null,
    statusCode: Int? = null,
    durationMs: Long? = null,
    extra: Map<String, Any?> = emptyMap()
) = appEvent(AppLogSeverity.INFO, event, context, "success", userId = userId, deviceId = deviceId, statusCode = statusCode, durationMs = durationMs, extra = extra)

fun Logger.appRejected(
    event: String,
    context: AppRequestContext,
    reason: String,
    userId: UUID? = null,
    deviceId: UUID? = null,
    statusCode: Int? = null,
    durationMs: Long? = null,
    extra: Map<String, Any?> = emptyMap()
) = appEvent(AppLogSeverity.WARN, event, context, "rejected", reason = reason, userId = userId, deviceId = deviceId, statusCode = statusCode, durationMs = durationMs, extra = extra)

fun Logger.appBlocked(
    event: String,
    context: AppRequestContext,
    reason: String,
    userId: UUID? = null,
    deviceId: UUID? = null,
    statusCode: Int? = null,
    durationMs: Long? = null,
    extra: Map<String, Any?> = emptyMap()
) = appEvent(AppLogSeverity.WARN, event, context, "blocked", reason = reason, userId = userId, deviceId = deviceId, statusCode = statusCode, durationMs = durationMs, extra = extra)

fun Logger.appFailure(
    event: String,
    context: AppRequestContext,
    reason: String,
    userId: UUID? = null,
    deviceId: UUID? = null,
    statusCode: Int? = null,
    durationMs: Long? = null,
    extra: Map<String, Any?> = emptyMap(),
    throwable: Throwable? = null
) = appEvent(AppLogSeverity.ERROR, event, context, "failed", reason = reason, userId = userId, deviceId = deviceId, statusCode = statusCode, durationMs = durationMs, extra = extra, throwable = throwable)

fun Logger.appRotated(
    event: String,
    context: AppRequestContext,
    userId: UUID? = null,
    deviceId: UUID? = null,
    extra: Map<String, Any?> = emptyMap()
) = appEvent(AppLogSeverity.INFO, event, context, "rotated", userId = userId, deviceId = deviceId, extra = extra)
