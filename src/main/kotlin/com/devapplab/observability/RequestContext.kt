package com.devapplab.observability

import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.callid.callId
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import java.util.UUID

data class AppRequestContext(
    val requestId: String,
    val method: String,
    val path: String,
    val platform: String? = null,
    val appVersion: String? = null,
    val buildNumber: String? = null,
    val osVersion: String? = null,
    val deviceModel: String? = null
)

private val requestContextKey = io.ktor.util.AttributeKey<AppRequestContext>("app_request_context")

fun ApplicationCall.requestContext(): AppRequestContext {
    val existing = attributes.getOrNull(requestContextKey)
    if (existing != null) return existing

    val created = AppRequestContext(
        requestId = callId ?: UUID.randomUUID().toString(),
        method = request.httpMethod.value,
        path = request.path(),
        platform = request.headers["X-Platform"],
        appVersion = request.headers["X-App-Version"],
        buildNumber = request.headers["X-Build-Number"],
        osVersion = request.headers["X-OS-Version"],
        deviceModel = request.headers["X-Device-Model"]
    )

    attributes.put(requestContextKey, created)
    return created
}
