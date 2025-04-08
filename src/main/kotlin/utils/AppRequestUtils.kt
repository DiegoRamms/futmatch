package com.devapplab.utils

import io.ktor.server.application.*

fun ApplicationCall.getAuthorizationHeader(): String? {
   return request.headers["Authorization"]
        ?.removePrefix("Bearer ")
        ?.trim()
}

fun ApplicationCall.getUserAgentHeader(): String? {
    return request.headers["User-Agent"]
}