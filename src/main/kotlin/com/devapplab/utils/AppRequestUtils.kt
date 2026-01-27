package com.devapplab.utils

import io.ktor.server.application.*

const val REFRESH_TOKEN_HEADER = "X-Refresh-Token"

@Suppress("Unused")
fun ApplicationCall.getAuthorizationHeader(): String? {
   return request.headers["Authorization"]
        ?.removePrefix("Bearer ")
        ?.trim()
}

fun ApplicationCall.getRefreshToken(): String? {
    return request.headers[REFRESH_TOKEN_HEADER]
}

fun ApplicationCall.getResetToken(): String {
    return request.headers["Authorization"]
        ?.removePrefix("Bearer ")
        ?.trim()
        ?: throw InvalidTokenException("Missing or malformed reset token in Authorization header")
}

fun ApplicationCall.getUserAgentHeader(): String? {
    return request.headers["User-Agent"]
}